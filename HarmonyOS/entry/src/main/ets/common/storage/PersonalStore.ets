import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { PersonalRecord, upsertRecord, toggleFavorite } from './RecordPolicy';
export class PersonalStore {
  private static instance: preferences.Preferences | undefined = undefined;
  static initialize(context: common.Context): void {
    PersonalStore.instance = preferences.getPreferencesSync(context, {name:'aiyifan_personal'});
  }
  static read(key: string): PersonalRecord[] {
    const raw = PersonalStore.instance?.getSync(key,'[]');
    try {
      const records: PersonalRecord[] = JSON.parse(typeof raw === 'string' ? raw : '[]');
      return Array.isArray(records) ? records.filter((r: PersonalRecord) => !!r && typeof r.mediaKey === 'string') : [];
    } catch (_) { return []; }
  }
  static async write(key: string, records: PersonalRecord[]): Promise<void> {
    if (!PersonalStore.instance) throw new Error('本地存储尚未初始化');
    PersonalStore.instance.putSync(key,JSON.stringify(records));
    await PersonalStore.instance.flush();
  }
  static async history(record: PersonalRecord): Promise<void> {
    await PersonalStore.write('history',upsertRecord(PersonalStore.read('history'),record));
  }
  static async favorite(record: PersonalRecord): Promise<void> {
    await PersonalStore.write('favorites',toggleFavorite(PersonalStore.read('favorites'),record));
  }
}
