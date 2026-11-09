import { http } from '@kit.NetworkKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { HomeSection, normalizeApi, parseHome } from './HomeCatalog';
import { CatalogDetail, CatalogEpisode, parseDetail, parsePlayback } from './VideoCatalog';
interface ApiConfiguration { api?: string; }
export class HomeRepository {
  private baseUrl: string = '';
  private async get(url: string): Promise<string> {
    const client = http.createHttp();
    try {
      const response = await client.request(url, {
        method: http.RequestMethod.GET,
        header: { 'User-Agent': 'Aiyifan/1.0', 'Accept': 'application/json,text/plain,*/*', 'Accept-Language': 'zh-CN', 'X-Region': 'cn' },
        connectTimeout: 10000, readTimeout: 10000,
        expectDataType: http.HttpDataType.STRING
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        hilog.warn(0x0000, 'CatalogNetwork', 'HTTP %{public}d response: %{public}s', response.responseCode, String(response.result).slice(0, 600));
        if (response.responseCode === 403 && String(response.result).includes('country_banned')) {
          throw new Error('服务暂不支持当前网络所在地区，请联系内容服务提供方');
        }
        throw new Error('HTTP ' + response.responseCode + ' ' + url);
      }
      if (typeof response.result === 'string') return response.result;
      return JSON.stringify(response.result);
    } finally {
      client.destroy();
    }
  }
  private async resolveBase(): Promise<string> {
    if (this.baseUrl) return this.baseUrl;
    let baseUrl = 'https://api.tripdata.app/';
    try {
      const config: ApiConfiguration = JSON.parse(await this.get('https://dataexbbff.github.io/rawApp.json'));
      if (config && typeof config.api === 'string') baseUrl = normalizeApi(config.api);
    } catch (_) { /* Configuration unavailable: use Android's default endpoint. */ }
    this.baseUrl = baseUrl;
    return baseUrl;
  }
  async detail(key: string): Promise<CatalogDetail> {
    return parseDetail(await this.get(await this.resolveBase() + 'api/Video/VideoDetails?mediaKey=' + encodeURIComponent(key)), key);
  }
  async playback(detail: CatalogDetail, episode: CatalogEpisode): Promise<string> {
    if (/^https?:\/\//.test(episode.mediaUrl)) return episode.mediaUrl;
    const query = 'mediaKey=' + encodeURIComponent(detail.mediaKey) + '&videoId=' + episode.uniqueId +
      '&resolution=' + encodeURIComponent(episode.resolution) + '&liveLine=&subtitlePrefer=&videoType=' + detail.videoType;
    return parsePlayback(await this.get(await this.resolveBase() + 'api/Video/getPlayData?' + query), episode.resolution).mediaUrl;
  }
  async load(): Promise<HomeSection[]> {
    const payload = await this.get(await this.resolveBase() + 'api/List/Index');
    const sections = parseHome(payload);
    if (sections.length === 0) throw new Error('首页响应没有可展示分区');
    return sections;
  }
}
