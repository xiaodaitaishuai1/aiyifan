import { Episode, PlaybackQuality, VideoDetail } from '../model/VideoModels';

export type PlaybackState = 'idle' | 'loading' | 'ready' | 'playing' | 'paused' | 'error';

export interface PlaybackSnapshot {
  state: PlaybackState;
  positionMs: number;
  durationMs: number;
  episodeKey?: string;
  errorMessage?: string;
}

export interface PlaybackController {
  prepare(detail: VideoDetail, episode: Episode, quality?: PlaybackQuality): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  seekTo(positionMs: number): Promise<void>;
  selectEpisode(episode: Episode): Promise<void>;
  selectQuality(quality: PlaybackQuality): Promise<void>;
  snapshot(): PlaybackSnapshot;
  release(): Promise<void>;
}
