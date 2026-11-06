@Entry
@Component
struct MainPage {
  @State selectedTab: number = 0;
  @State darkMode: boolean = true;

  private tabTitles: string[] = ['首页', '热门', '我的'];

  build() {
    Column() {
      Row() {
        Text('爱壹帆').fontSize(24).fontWeight(FontWeight.Bold).fontColor(Color.White);
        Blank();
        Button(this.darkMode ? '深色' : '浅色')
          .fontSize(13)
          .backgroundColor('#2A2C33')
          .onClick(() => this.darkMode = !this.darkMode);
      }
      .width('100%')
      .padding({ left: 20, right: 20, top: 18, bottom: 12 });

      Stack() {
        Column() {
          Text(this.tabTitles[this.selectedTab])
            .fontSize(26)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .margin({ bottom: 12 });
          Text('鸿蒙客户端基础骨架已就绪')
            .fontSize(16)
            .fontColor('#B7BAC4');
          Text('下一阶段接入首页目录、搜索和播放接口')
            .fontSize(14)
            .fontColor('#777B88')
            .margin({ top: 8 });
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
              .fontColor(this.selectedTab === index ? '#FFFFFF' : '#888B96');
          }
          .layoutWeight(1)
          .padding(16)
          .onClick(() => this.selectedTab = index);
        });
      }
      .width('100%')
      .backgroundColor('#1A1B20');
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.darkMode ? '#101114' : '#F6F7F9');
  }
}
