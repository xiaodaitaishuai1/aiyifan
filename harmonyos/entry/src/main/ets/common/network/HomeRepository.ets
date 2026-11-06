import { http } from '@kit.NetworkKit';
import { HomeSection, normalizeApi, parseHome } from './HomeCatalog';
interface ApiConfiguration { api?: string; }
export class HomeRepository {
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
        throw new Error('HTTP ' + response.responseCode + ' ' + url);
      }
      if (typeof response.result === 'string') return response.result;
      return JSON.stringify(response.result);
    } finally {
      client.destroy();
    }
  }
  async load(): Promise<HomeSection[]> {
    let baseUrl = 'https://api.tripdata.app/';
    try {
      const config: ApiConfiguration = JSON.parse(await this.get('https://dataexbbff.github.io/rawApp.json'));
      if (config && typeof config.api === 'string') baseUrl = normalizeApi(config.api);
    } catch (_) { /* Configuration unavailable: use Android's default endpoint. */ }
    const payload = await this.get(baseUrl + 'api/List/Index');
    const sections = parseHome(payload);
    if (sections.length === 0) throw new Error('首页响应没有可展示分区');
    return sections;
  }
}
