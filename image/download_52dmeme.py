from pathlib import Path
from io import BytesIO
from urllib.parse import urlencode, urljoin
import hashlib
import json
import time
from PIL import Image
from playwright.sync_api import sync_playwright

BASE = "https://52dmeme.com"
AUTHOR = "baicai492"
URL = f"{BASE}/u/{AUTHOR}"
LOGIN_URL = f"{BASE}/account-password?next=%2F"
# 按下载文件的真实尺寸过滤，不用列表缩略图尺寸。
MIN_WIDTH = 200
MIN_HEIGHT = 200
MIN_AREA = 60000
OUT = Path(__file__).resolve().parent / f"52dmeme_{AUTHOR}"
MANIFEST = OUT / "progress.json"
MAX_PAGES = 10000


def save_progress(state):
    temporary = MANIFEST.with_suffix('.tmp')
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary.replace(MANIFEST)


def image_urls(items):
    for item in items:
        media = item.get('images') or ([{'u': item['image']}] if item.get('image') else [])
        for picture in media:
            if picture.get('v'):  # 不下载视频的封面
                continue
            source = picture.get('o') or picture.get('u')
            if source:
                address = urljoin(BASE, source)
                if address.startswith(('https://', 'http://')):
                    yield address


def fetch_json(page, address):
    for attempt in range(3):
        result = page.evaluate('''async address => {
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 30000);
            try {
                const r = await fetch(address, {credentials: 'same-origin', signal: controller.signal});
                return {status: r.status, text: await r.text()};
            } finally {clearTimeout(timer);}
        }''', address)
        if result['status'] in (401, 403):
            raise RuntimeError('登录已失效或当前账号没有访问权限，请重新运行并登录。')
        if result['status'] == 429 or result['status'] >= 500:
            if attempt < 2:
                page.wait_for_timeout(3000 * (attempt + 1))
                continue
        if result['status'] != 200:
            raise RuntimeError(f"接口返回 HTTP {result['status']}")
        data = json.loads(result['text'])
        if not isinstance(data.get('items'), list) or not isinstance(data.get('more'), bool):
            raise RuntimeError('接口结构变化，停止抓取；已下载图片保留。')
        return data


def download(context, address, state):
    prior = state.get(address, {})
    if prior.get('status') == 'saved' and (OUT / prior['file']).is_file():
        return 'existing'
    # 被过滤和失败的图片会在下次运行时重新检查，允许调整尺寸阈值。
    for attempt in range(3):
        response = None
        try:
            response = context.request.get(address, timeout=60000)
            if not response.ok:
                raise RuntimeError(f'HTTP {response.status}')
            body = response.body()
            with Image.open(BytesIO(body)) as picture:
                width, height = picture.size
                fmt = picture.format
            if width < MIN_WIDTH or height < MIN_HEIGHT or width * height < MIN_AREA:
                state[address] = {'status': 'filtered', 'width': width, 'height': height}
                return 'filtered'
            suffix = {'.jpeg': '.jpg'}.get('.' + fmt.lower(), '.' + fmt.lower())
            name = hashlib.sha256(body).hexdigest() + suffix
            target = OUT / name
            if not target.exists():
                temporary = target.with_suffix(target.suffix + '.part')
                temporary.write_bytes(body)
                temporary.replace(target)
            state[address] = {'status': 'saved', 'file': name, 'width': width, 'height': height}
            return 'saved'
        except Exception as error:
            if attempt == 2:
                state[address] = {'status': 'failed', 'error': str(error)}
                return 'failed'
            time.sleep(2 * (attempt + 1))
        finally:
            if response is not None:
                response.dispose()


def main():
    OUT.mkdir(exist_ok=True)
    state = json.loads(MANIFEST.read_text(encoding='utf-8')) if MANIFEST.exists() else {}
    counts = dict(saved=0, existing=0, filtered=0, failed=0)
    urls_seen, posts_seen = set(), set()
    completed = False
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False, channel='chrome')
        try:
            context = browser.new_context()
            page = context.new_page()
            page.goto(LOGIN_URL, wait_until='domcontentloaded')
            input('请在弹出的浏览器里手动登录，确认成功后回到终端按回车...')
            page.goto(URL, wait_until='domcontentloaded')
            for number in range(1, MAX_PAGES + 1):
                query = urlencode({'author': AUTHOR, 'sort': 'new', 'page': number})
                data = fetch_json(page, f'{BASE}/api/memes?{query}')
                items = data['items']
                identities = {str(item['id']) for item in items}
                if items and not identities.difference(posts_seen):
                    raise RuntimeError('接口重复返回同一页，停止以避免死循环。')
                posts_seen.update(identities)
                if not items and data['more']:
                    raise RuntimeError('接口返回空页但提示还有下一页，请稍后重新运行。')
                for address in image_urls(items):
                    if address in urls_seen:
                        continue
                    urls_seen.add(address)
                    status = download(context, address, state)
                    counts[status] += 1
                    save_progress(state)
                    print(f"图片 {len(urls_seen)}: {status}", flush=True)
                print(f"第 {number} 页：累计 {len(posts_seen)} 个帖子，{len(urls_seen)} 个图片地址；{counts}", flush=True)
                if not data['more']:
                    completed = True
                    break
                page.wait_for_timeout(1000)
            if not completed:
                raise RuntimeError('达到分页安全上限，尚未取完。')
            if not posts_seen:
                print('接口没有返回帖子。请确认已登录且该账号可以查看目标主页；不能据此认定作者没有图片。')
            else:
                print('接口分页结束。只能获取当前账号有权限访问的帖子图片。')
        finally:
            save_progress(state)
            failures = {u: r for u, r in state.items() if r.get('status') == 'failed'}
            (OUT / 'failed.json').write_text(json.dumps(failures, ensure_ascii=False, indent=2), encoding='utf-8')
            browser.close()
    print(f'结果：{counts}；保存目录：{OUT}')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('\n已停止，进度已保存。重新运行会跳过已下载文件。')
    except Exception as error:
        print(f'未完成：{error}')
        raise SystemExit(1)
