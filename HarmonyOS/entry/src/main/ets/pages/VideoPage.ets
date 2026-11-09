import { router } from '@kit.ArkUI';
import { HomeRepository } from '../common/network/HomeRepository';
import { CatalogDetail, CatalogEpisode } from '../common/network/VideoCatalog';
import { PersonalStore } from '../common/storage/PersonalStore';
import { PersonalRecord, resumePosition } from '../common/storage/RecordPolicy';
interface VideoRoute { mediaKey: string; }
@Entry
@Component
struct VideoPage {
  @State detail: CatalogDetail = { mediaKey:'', title:'视频详情', description:'', coverUrl:'', videoType:0, episodes:[] };
  @State message: string = '正在加载详情…';
  @State source: string = '';
  @State selectedKey: string = '';
  @State loading: boolean = false;
  @State favorite: boolean = false;
  private playbackPosition: number = 0;
  private duration: number = 0;
  private lastSaved: number = 0;
  private resume: number = 0;
  private repository: HomeRepository = new HomeRepository();
  private controller: VideoController = new VideoController();
  private revision: number = 0;
  private active: boolean = true;
  aboutToAppear() { this.active = true; this.loadDetail(); }
  aboutToDisappear() { this.saveProgress(); this.active = false; this.revision++; this.controller.stop(); }
  onPageHide() { this.saveProgress(); this.controller.pause(); }
  private record(): PersonalRecord {
    return {mediaKey:this.detail.mediaKey,title:this.detail.title,coverUrl:this.detail.coverUrl,episodeKey:this.selectedKey,position:this.playbackPosition,duration:this.duration,updatedAt:Date.now()};
  }
  private saveProgress(): void {
    if (!this.detail.mediaKey || !this.selectedKey || this.playbackPosition <= 0) return;
    PersonalStore.history(this.record()).catch(() => { if (this.active) this.message = '观看记录保存失败'; });
  }
  private async toggleFavorite(): Promise<void> {
    try {
      await PersonalStore.favorite(this.record());
      this.favorite = PersonalStore.read('favorites').some((r: PersonalRecord) => r.mediaKey === this.detail.mediaKey);
    } catch (_) { this.message = '收藏保存失败，请重试'; }
  }
  private async loadDetail(): Promise<void> {
    const params = router.getParams() as VideoRoute;
    if (!params || !params.mediaKey) { this.message = '缺少视频标识'; return; }
    this.loading = true;
    this.message = '正在加载详情…';
    try {
      const detail = await this.repository.detail(params.mediaKey);
      if (!this.active) return;
      this.detail = detail;
      this.favorite = PersonalStore.read('favorites').some((r: PersonalRecord) => r.mediaKey === detail.mediaKey);
      this.message = detail.episodes.length ? '请选择剧集播放' : '暂无可播放剧集';
      const previous = PersonalStore.read('history').find((r: PersonalRecord) => r.mediaKey === detail.mediaKey);
      const episode = detail.episodes.find((e: CatalogEpisode) => e.episodeKey === previous?.episodeKey) || detail.episodes[0];
      if (episode) await this.selectEpisode(episode);
    } catch (error) { if (this.active) this.message = '详情加载失败：' + String(error).slice(0,100); }
    finally { if (this.active) this.loading = false; }
  }
  private async selectEpisode(episode: CatalogEpisode): Promise<void> {
    this.saveProgress();
    const revision = ++this.revision;
    this.controller.stop();
    this.source = '';
    this.selectedKey = episode.episodeKey;
    this.playbackPosition = 0;
    this.duration = 0;
    this.resume = resumePosition(PersonalStore.read('history').find((r: PersonalRecord) => r.mediaKey === this.detail.mediaKey), episode.episodeKey);
    this.message = '正在解析播放地址…';
    try {
      const source = await this.repository.playback(this.detail, episode);
      if (!this.active || revision !== this.revision) return;
      this.source = source;
      this.message = '';
    } catch (error) {
      if (this.active && revision === this.revision) this.message = '播放加载失败：' + String(error).slice(0,100);
    }
  }
  build() {
    Column() {
      Row() {
        Button('返回').onClick(() => router.back());
        Text(this.detail.title).fontSize(19).fontColor('#FFFFFF').maxLines(2).layoutWeight(1).margin({left:12});
      }.width('100%').padding(16);
      if (this.source) {
        Video({src:this.source, controller:this.controller})
          .width('100%').height(240).autoPlay(true).controls(true)
          .onPrepared((event) => {
            this.duration = event.duration;
            if (this.resume > 0) { this.controller.setCurrentTime(this.resume, SeekMode.Accurate); this.resume = 0; }
          })
          .onUpdate((event) => {
            this.playbackPosition = event.time;
            if (Date.now() - this.lastSaved >= 5000) { this.lastSaved = Date.now(); this.saveProgress(); }
          })
          .onPause(() => this.saveProgress())
          .onFinish(() => { this.playbackPosition = this.duration; this.saveProgress(); })
          .onError(() => { this.message = '视频播放失败，请重新选择剧集重试'; });
      }
      Scroll() {
        Column({space:14}) {
          if (this.detail.mediaKey) { Button(this.favorite ? '取消收藏' : '收藏').onClick(() => this.toggleFavorite()); }
          if (this.message) { Text(this.message).fontColor('#E6B85C').fontSize(15); }
          if (this.message.startsWith('详情加载失败')) { Button('重新加载').enabled(!this.loading).onClick(() => this.loadDetail()); }
          Text(this.detail.description || '暂无简介').fontColor('#B7BAC4').fontSize(14).width('100%');
          Text('选集').fontSize(20).fontColor('#FFFFFF').width('100%');
          Flex({wrap:FlexWrap.Wrap}) {
            ForEach(this.detail.episodes, (episode: CatalogEpisode) => {
              Button(episode.title)
                .fontSize(14).margin(4)
                .backgroundColor(this.selectedKey === episode.episodeKey ? '#84611E' : '#2A2C33')
                .fontColor('#FFFFFF')
                .onClick(() => this.selectEpisode(episode));
            }, (episode: CatalogEpisode) => episode.episodeKey);
          }.width('100%');
        }.padding(16).width('100%');
      }.layoutWeight(1).width('100%');
    }.width('100%').height('100%').backgroundColor('#101114');
  }
}

