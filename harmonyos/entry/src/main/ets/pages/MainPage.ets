import { HomeRepository } from '../common/network/HomeRepository';
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

  private tabTitles: string[] = ['首页', '热门', '我的'];
  private repository: HomeRepository = new HomeRepository();

  aboutToAppear() {
    this.loadHome();
  }

  private async loadHome(): Promise<void> {
    this.status = '正在加载…';
    try {
      this.sections = await this.repository.load();
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
        .width('calc(100% - 40vp)')
        .height(44)
        .margin({ left: 20, right: 20, bottom: 12 })
        .onChange((value: string) => {
          this.query = value;
          this.searchResults = searchCatalog(this.sections, value);
        });

      Scroll() {
        Column() {
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
                Text(video.title || '未命名视频').fontSize(16).fontColor(this.darkMode ? '#FFFFFF' : '#17181C').layoutWeight(1);
                Text(video.updateStatus).fontSize(12).fontColor('#777B88');
              }
              .width('100%')
              .padding({ top: 12, bottom: 12 })
              .border({ width: { bottom: 1 }, color: this.darkMode ? '#252731' : '#E3E5EA' });
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
          .onClick(() => this.selectedTab = index);
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


