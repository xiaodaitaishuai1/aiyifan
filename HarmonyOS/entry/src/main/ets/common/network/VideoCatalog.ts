export interface CatalogEpisode {
  episodeKey: string;
  uniqueId: number;
  title: string;
  mediaUrl: string;
  resolution: string;
}
export interface CatalogDetail {
  mediaKey: string;
  title: string;
  description: string;
  coverUrl: string;
  videoType: number;
  episodes: CatalogEpisode[];
}
export interface PlaySource { mediaUrl: string; resolution: string; }
interface RawEpisode { episodeKey?: string; episodeId?: number; episodeTitle?: string; mediaUrl?: string; resolution?: string; }
interface RawDetail { mediaKey?: string; title?: string; introduce?: string; coverImgUrl?: string; videoType?: number; episodes?: RawEpisode[]; }
interface DetailResponse { data?: {detailInfo?: RawDetail}; }
interface RawSource { mediaUrl?: string; resolution?: string; isDefault?: boolean; }
interface PlayResponse { data?: {list?: RawSource[]}; }
function value(input: string | undefined): string { return typeof input === 'string' && input !== 'null' ? input.trim() : ''; }
export function parseDetail(payload: string, key: string): CatalogDetail {
  const response: DetailResponse = JSON.parse(payload);
  const raw = response?.data?.detailInfo;
  if (!raw || value(raw.mediaKey) !== key || !value(raw.title)) throw new Error('视频详情无效');
  const episodes: CatalogEpisode[] = [];
  const seen: Set<string> = new Set<string>();
  if (Array.isArray(raw.episodes)) raw.episodes.forEach((e: RawEpisode, index: number) => {
    if (!e || !value(e.episodeKey) || seen.has(value(e.episodeKey))) return;
    seen.add(value(e.episodeKey));
    episodes.push({episodeKey:value(e.episodeKey), uniqueId:Number(e.episodeId) || 0, title:value(e.episodeTitle) || String(index+1), mediaUrl:value(e.mediaUrl), resolution:value(e.resolution)});
  });
  return {mediaKey:key, title:value(raw.title), description:value(raw.introduce), coverUrl:value(raw.coverImgUrl), videoType:Number(raw.videoType) || 0, episodes:episodes};
}
export function parsePlayback(payload: string, resolution: string): PlaySource {
  const response: PlayResponse = JSON.parse(payload);
  const sources = Array.isArray(response?.data?.list) ? response.data.list.filter((s: RawSource) => s && /^https?:\/\//.test(value(s.mediaUrl)) && !!value(s.resolution)) : [];
  const source = sources.find((s: RawSource) => value(s.resolution) === resolution) || sources.find((s: RawSource) => s.isDefault) || sources[0];
  if (!source) throw new Error('暂无可播放地址');
  return {mediaUrl:value(source.mediaUrl), resolution:value(source.resolution)};
}
