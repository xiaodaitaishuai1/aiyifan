import { HomeRepository } from '../common/network/HomeRepository';
import { router } from '@kit.ArkUI';
import { PersonalStore } from '../common/storage/PersonalStore';
import { PersonalRecord } from '../common/storage/RecordPolicy';
import { HomeSection, HomeVideo, searchCatalog } from '../common/network/HomeCatalog';

@Entry
@Component
struct MainPage {
  @State selectedTab: number = 0;
  @State darkMode: boolean = true;
  @State sections: HomeSection[] = [];
  @State status: string = '正在加载…';
  @State query: string = '';
  @State searchResults: HomeVideo[] = [];
  @State favorites: PersonalRecord[] = [];
  @State history: PersonalRecord[] = [];
  onPageShow() { this.reloadPersonal(); }
  private reloadPersonal(): void {
    this.favorites = PersonalStore.read('favorites');
    this.history = PersonalStore.read('history');
  }
  private openVideo(key: string): void {
    router.pushUrl({url:'pages/VideoPage',params:{mediaKey:key}}).catch(() => { this.status = '打开详情失败'; });
  }

  private tabTitles: string[] = ['首页', '热门', '我的'];
  private repository: HomeRepository = new HomeRepository();

  aboutToAppear() {
    this.loadHome();
  }

  private async loadHome(): Promise<void> {
    this.status = '正在加载…';
    try {
      this.sections = await this.repository.load();
      this.searchResults = searchCatalog(this.sections, this.query);
      this.status = this.sections.length > 0 ? '' : '暂无内容';
    } catch (error) {
      const message = String(error).replace('Error: ', '');
      this.status = '加载失败：' + message.slice(0, 80);
    }
  }

  private visibleSections(): HomeSection[] {
    if (this.selectedTab === 0) return this.sections;
    if (this.selectedTab === 1) return this.sections.slice(0, 3);
    return [];
  }

  build() {
    Column() {
      Row() {
        Text('爱壹帆').fontSize(24).fontWeight(FontWeight.Bold).fontColor(this.darkMode ? '#FFFFFF' : '#17181C');
        Blank();
        Button(this.darkMode ? '深色' : '浅色')
          .fontSize(13)
          .backgroundColor('#2A2C33')
          .onClick(() => this.darkMode = !this.darkMode);
      }
      .width('100%')
      .padding({ left: 20, right: 20, top: 18, bottom: 12 });

      TextInput({ placeholder: '搜索视频', text: this.query })
        .fontColor(this.darkMode ? '#FFFFFF' : '#17181C')
        .placeholderColor(this.darkMode ? '#B7BAC4' : '#616670')
        .backgroundColor(this.darkMode ? '#252731' : '#E9EBEF')
        .width('calc(100% - 40vp)')
        .height(44)
        .margin({ left: 20, right: 20, bottom: 12 })
        .onChange((value: string) => {
          this.query = value;
          this.searchResults = searchCatalog(this.sections, value);
        });

      Scroll() {
        Column() {
          if (this.selectedTab === 2) {
            Text('我的收藏').fontSize(20).fontColor(this.darkMode ? '#FFFFFF' : '#17181C').margin(12);
            if (!this.favorites.length) { Text('暂无收藏').fontColor('#888B96'); }
            ForEach(this.favorites, (record: PersonalRecord) => {
              Button(record.title).margin(6).onClick(() => this.openVideo(record.mediaKey));
            }, (record: PersonalRecord) => record.mediaKey);
            Text('观看记录').fontSize(20).fontColor(this.darkMode ? '#FFFFFF' : '#17181C').margin(12);
            if (!this.history.length) { Text('暂无观看记录').fontColor('#888B96'); }
            ForEach(this.history, (record: PersonalRecord) => {
              Button(record.title + ' · ' + Math.floor(record.position / 60) + '分').margin(6).onClick(() => this.openVideo(record.mediaKey));
            }, (record: PersonalRecord) => record.mediaKey);
          }
          Text(this.status)
            .fontSize(16)
            .fontColor(this.status.includes('失败') ? '#E06C75' : (this.darkMode ? '#B7BAC4' : '#454952'))
            .margin({ bottom: 16 })
            .onClick(() => this.status.includes('失败') && this.loadHome());
          ForEach(this.query.trim().length > 0 ? [{ name: '搜索结果', videos: this.searchResults }] : this.visibleSections(), (section: HomeSection) => {
            Text(section.name)
              .fontSize(20)
              .fontWeight(FontWeight.Bold)
              .fontColor(this.darkMode ? '#FFFFFF' : '#17181C')
              .margin({ top: 8, bottom: 10 });
            ForEach(section.videos.slice(0, 12), (video: HomeVideo) => {
              Row() {
                if (video.coverUrl) {
                  Image(video.coverUrl).width(72).height(96).objectFit(ImageFit.Cover).borderRadius(6).margin({right:12});
                }
                Text(video.title || '未命名视频').fontSize(16).fontColor(this.darkMode ? '#FFFFFF' : '#17181C').layoutWeight(1);
                Text(video.updateStatus).fontSize(12).fontColor('#777B88');
              }
              .width('100%')
              .padding({ top: 12, bottom: 12 })
              .border({ width: { bottom: 1 }, color: this.darkMode ? '#252731' : '#E3E5EA' })
              .onClick(() => {
                router.pushUrl({url:'pages/VideoPage', params:{mediaKey:video.mediaKey}}).catch(() => {
                  this.status = '打开详情失败，请重试';
                });
              });
            });
          });
        }
        .alignItems(HorizontalAlign.Start)
        .padding(20);
      }
      .layoutWeight(1)
      .width('100%');

      Row() {
        ForEach(this.tabTitles, (title: string, index: number) => {
          Column() {
            Text(title)
              .fontSize(14)
              .fontColor(this.selectedTab === index ? (this.darkMode ? '#FFFFFF' : '#17181C') : '#777B88');
          }
          .layoutWeight(1)
          .padding(16)
          .onClick(() => { this.selectedTab = index; this.reloadPersonal(); });
        });
      }
      .width('100%')
      .backgroundColor(this.darkMode ? '#1A1B20' : '#FFFFFF');
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.darkMode ? '#101114' : '#F6F7F9');
  }
}


