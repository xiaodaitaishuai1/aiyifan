import { Category, VideoDetail, VideoSummary } from '../model/VideoModels';

export interface CatalogApi {
  getCategories(): Promise<Category[]>;
  getHome(categoryId: string, page: number): Promise<VideoSummary[]>;
  search(keyword: string, page: number): Promise<VideoSummary[]>;
  getDetail(mediaKey: string): Promise<VideoDetail>;
}
