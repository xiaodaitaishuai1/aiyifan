import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { PersonalStore } from '../common/storage/PersonalStore';

export default class EntryAbility extends UIAbility {
  onCreate(_want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    try { PersonalStore.initialize(this.context); }
    catch (_) { hilog.error(0x0000, 'PersonalStore', 'Storage initialization failed'); }
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/MainPage').catch(() => {
      hilog.error(0x0000, 'EntryAbility', 'Failed to load main page');
    });
  }

  onWindowStageDestroy(): void {}
  onForeground(): void {}
  onBackground(): void {}
}
