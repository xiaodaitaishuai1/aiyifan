export interface PersonalRecord {
  mediaKey: string; title: string; coverUrl: string;
  episodeKey: string; position: number; duration: number; updatedAt: number;
}
export function upsertRecord(records: PersonalRecord[], record: PersonalRecord): PersonalRecord[] {
  return [record].concat(records.filter((r: PersonalRecord) => r.mediaKey !== record.mediaKey)).slice(0,200);
}
export function toggleFavorite(records: PersonalRecord[], record: PersonalRecord): PersonalRecord[] {
  return records.some((r: PersonalRecord) => r.mediaKey === record.mediaKey)
    ? records.filter((r: PersonalRecord) => r.mediaKey !== record.mediaKey) : upsertRecord(records,record);
}
export function resumePosition(record: PersonalRecord | undefined, key: string): number {
  if (!record || record.episodeKey !== key || !Number.isFinite(record.position) || record.position < 0) return 0;
  if (record.duration > 0 && record.position >= record.duration - 2) return 0;
  return record.position;
}
