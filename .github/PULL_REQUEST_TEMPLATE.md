## 改了什么

<!-- 一句话说清这个 PR 解决什么问题，不要只写「修复 bug」 -->

## 验证方式

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 通过
- [ ] 在真机上跑过（型号与系统版本：＿＿＿）

没实测的路径写在下面：

## 自查

- [ ] 没有把最低系统要求抬到 Android 6.0 以上
- [ ] 新加的可交互元素能用方向键到达、有焦点态、能用确认键触发
- [ ] 没有引入 AndroidX 之外的第三方依赖
- [ ] 没有把签名库、口令、配对码、个人照片或日志提交进来
- [ ] 改了 `assets/wallpapers/catalog.json` 的话，条数仍在 15 条以内
