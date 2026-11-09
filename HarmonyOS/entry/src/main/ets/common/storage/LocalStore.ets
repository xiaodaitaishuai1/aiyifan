import { AuthSession, FavoriteRecord, WatchRecord } from '../model/VideoModels';

export interface LocalStore {
  getWatchRecords(): Promise<WatchRecord[]>;
  saveWatchRecord(record: WatchRecord): Promise<void>;
  clearWatchRecords(): Promise<void>;
  getFavorites(): Promise<FavoriteRecord[]>;
  saveFavorite(record: FavoriteRecord): Promise<void>;
  removeFavorite(mediaKey: string): Promise<void>;
  getSession(): Promise<AuthSession | undefined>;
  saveSession(session: AuthSession): Promise<void>;
  clearSession(): Promise<void>;
  getThemeMode(): Promise<'system' | 'light' | 'dark'>;
  saveThemeMode(mode: 'system' | 'light' | 'dark'): Promise<void>;
}
