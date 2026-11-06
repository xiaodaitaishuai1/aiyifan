export interface Category {
  id: string;
  name: string;
  type: number;
  styleType: number;
  url?: string;
}

export interface VideoSummary {
  mediaKey: string;
  title: string;
  coverUrl: string;
  videoType: number;
  contentType?: string;
  score?: string;
  playCount: number;
  updateStatus?: string;
  duration?: string;
  year?: string;
  area?: string;
}

export interface Episode {
  episodeKey: string;
  episodeTitle: string;
  uniqueId: number;
  mediaUrl?: string;
  resolution?: string;
  lang?: string;
  duration?: string;
  watchProgressMs: number;
}

export interface PlaybackQuality {
  resolution: string;
  description: string;
  mediaUrl: string;
  isDefault: boolean;
}

export interface PlaybackLanguage {
  mediaKey: string;
  name: string;
}

export interface VideoDetail extends VideoSummary {
  typeName?: string;
  director?: string;
  actor?: string;
  introduce?: string;
  comments: number;
  shareCount: number;
  publishTime?: string;
  updateMsg?: string;
  commentEnabled: boolean;
  episodes: Episode[];
  qualities: PlaybackQuality[];
  languages: PlaybackLanguage[];
  related: VideoSummary[];
}

export interface WatchRecord {
  mediaKey: string;
  episodeKey: string;
  title: string;
  coverUrl: string;
  progressMs: number;
  durationMs: number;
  updatedAt: number;
}

export interface FavoriteRecord {
  mediaKey: string;
  title: string;
  coverUrl: string;
  videoType: number;
  createdAt: number;
}

export interface AuthSession {
  token: string;
  userId: string;
  nickname: string;
  avatarUrl?: string;
}
