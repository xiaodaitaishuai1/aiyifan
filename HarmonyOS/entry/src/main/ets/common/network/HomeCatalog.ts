export interface HomeVideo {
  mediaKey: string;
  title: string;
  coverUrl: string;
  updateStatus: string;
}
export interface HomeSection { name: string; videos: HomeVideo[]; }
interface RemoteVideo { mediaKey?: string; title?: string; coverImgUrl?: string; updateStatus?: string; videoType?: number; type?: number; }
interface RemoteSection { name?: string; list?: RemoteVideo[]; }
interface HomeResponse { data?: { list?: RemoteSection[] }; }
function text(value: string | undefined): string {
  return typeof value === 'string' && value.trim() !== 'null' ? value.trim() : '';
}
export function normalizeApi(value: string): string {
  const url = value.trim();
  if (!/^https:\/\/[^\s/?#]+(?:\/[^\s?#]*)?$/.test(url)) return 'https://api.tripdata.app/';
  return url.endsWith('/') ? url : url + '/';
}
export function parseHome(payload: string): HomeSection[] {
  const response: HomeResponse = JSON.parse(payload);
  const sections: HomeSection[] = [];
  if (!response || !response.data || !Array.isArray(response.data.list)) return sections;
  response.data.list.forEach((section: RemoteSection) => {
    if (!section || !text(section.name) || !Array.isArray(section.list)) return;
    const videos: HomeVideo[] = [];
    const seen: Set<string> = new Set<string>();
    section.list.forEach((item: RemoteVideo) => {
      if (!item || !text(item.mediaKey) || (item.videoType ?? item.type) === 3) return;
      const key = text(item.mediaKey);
      if (seen.has(key)) return;
      seen.add(key);
      videos.push({mediaKey:key, title:text(item.title), coverUrl:text(item.coverImgUrl), updateStatus:text(item.updateStatus)});
    });
    if (videos.length > 0) sections.push({name:text(section.name), videos:videos});
  });
  return sections;
}
export function searchCatalog(sections: HomeSection[], query: string): HomeVideo[] {
  const keyword = query.trim().toLocaleLowerCase();
  const result: HomeVideo[] = [];
  const seen: Set<string> = new Set<string>();
  if (!keyword) return result;
  sections.forEach((section: HomeSection) => {
    section.videos.forEach((video: HomeVideo) => {
      if (!seen.has(video.mediaKey) && (video.title.toLocaleLowerCase().includes(keyword) || section.name.toLocaleLowerCase().includes(keyword))) {
        seen.add(video.mediaKey);
        result.push(video);
      }
    });
  });
  return result;
}
