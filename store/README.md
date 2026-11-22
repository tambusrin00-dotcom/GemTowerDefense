# Play 스토어 등록 자료

## 파일 구성
```
store/
├─ fastlane/metadata/android/ko-KR/      ← Play Console에 그대로 복사 (fastlane supply 형식)
│  ├─ title.txt                 앱 이름 (9자 / 최대 30자)
│  ├─ short_description.txt     간단한 설명 (45자 / 최대 80자)
│  ├─ full_description.txt      자세한 설명 (953자 / 최대 4000자)
│  ├─ changelogs/1.txt          출시 노트 (versionCode 1)
│  └─ images/
│     ├─ icon.png               앱 아이콘 512×512
│     ├─ featureGraphic.png     그래픽 이미지 1024×500
│     └─ phoneScreenshots/      휴대전화 스크린샷 1920×1080 × 4장
├─ privacy-policy.md            개인정보처리방침 원문
├─ privacy/index.html           개인정보처리방침 공개용 웹페이지 (CONTACT_EMAIL 두 곳을 바꾼 뒤 호스팅)
├─ screenshots-raw/             캡션 넣기 전 원본 스크린샷
└─ tools/StoreArt.java          아이콘·그래픽·스크린샷 생성기
```

## 1. 기본 정보 (기본 스토어 등록정보)
| 항목 | 입력값 |
|---|---|
| 기본 언어 | 한국어 – ko-KR |
| 앱 이름 | `title.txt` |
| 간단한 설명 | `short_description.txt` |
| 자세한 설명 | `full_description.txt` |
| 앱 아이콘 | `images/icon.png` |
| 그래픽 이미지 | `images/featureGraphic.png` |
| 휴대전화 스크린샷 | `images/phoneScreenshots/` 4장 (순서대로) |
| 앱 또는 게임 | 게임 |
| 카테고리 | 전략 |
| 태그(최대 5개) | 타워 디펜스, 전략, 퍼즐 전략, 오프라인, 싱글 플레이어 (Console 목록에 있는 것 중 선택) |
| 유료/무료 | 무료 |
| 연락처 이메일 | **직접 입력** (스토어에 공개됨) |

## 2. 앱 콘텐츠 (정책 → 앱 콘텐츠)
| 항목 | 답변 | 근거 |
|---|---|---|
| 개인정보처리방침 | https://tambusrin00-dotcom.github.io/GemTowerDefense/ (gh-pages 브랜치) | 모든 앱 필수 |
| 광고 | **아니요**, 광고 없음 | 광고 SDK 없음 |
| 앱 액세스 권한 | **모든 기능을 별도 제한 없이 이용 가능** | 로그인 없음 |
| 콘텐츠 등급 (IARC 설문) | 카테고리: 게임 · 폭력: 사람·동물이 아닌 **추상적인 도형**이 공격받아 사라짐, 피·신체 훼손 없음 · 성적 콘텐츠/언어/약물/도박: 없음 · 사용자 간 소통/위치 공유/디지털 구매: 없음 | 예상 등급은 전체이용가 계열이지만 최종 등급은 설문 결과로 정해집니다 |
| 타겟층 및 콘텐츠 | 권장: **13세 이상** 연령대만 선택. 13세 미만을 포함하면 가족 정책 심사가 추가됩니다 (광고·데이터 수집이 없어서 충족은 가능하지만 확인 항목이 늘어남) | |
| 데이터 보안 | **수집하는 데이터 없음 / 공유하는 데이터 없음** → "사용자 데이터를 수집하거나 공유하지 않음" | 릴리스 매니페스트에 인터넷 권한 없음, 분석·광고·로그인 SDK 없음 |
| 정부 앱 / 금융 기능 / 건강 / 뉴스 | 모두 해당 없음 | |

> 앱에 광고·분석·로그인·인터넷 기능을 나중에 추가하면 **데이터 보안, 광고, 개인정보처리방침을 반드시 다시 수정**해야 합니다.

## 3. 출시 순서 제안
1. `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
2. Play Console에서 앱 만들기 → 위 1·2번 항목 입력
3. **Play 앱 서명** 사용 (기본값). 지금 키스토어는 업로드 키가 됨
4. 내부 테스트 트랙에 AAB 업로드 → 실제 기기에서 확인
5. 신규 **개인** 개발자 계정이면 프로덕션 출시 전에 비공개 테스트(일정 인원 이상 테스터, 일정 기간 이상)가 필요합니다. 요건은 바뀔 수 있으니 Play Console 대시보드에 표시되는 기준을 확인하세요
6. 다음 업데이트부터는 `app/build.gradle.kts`의 `versionCode`를 1씩 올리고 `changelogs/<versionCode>.txt`를 추가

## 스크린샷 다시 만들기
화면이 바뀌면 debug 빌드의 스크린샷 전용 화면으로 다시 찍을 수 있습니다 (release 빌드에는 포함되지 않음).
```
./gradlew installDebug
adb shell am start -n com.tambusrin.gemtower/.ScreenshotActivity --es scene maze     # maze | choose | recipes | boss
adb exec-out screencap -p > store/screenshots-raw/maze.png                           # 장면마다 반복 (10초쯤 기다린 뒤)
java store/tools/StoreArt.java store/screenshots-raw store/fastlane/metadata/android/ko-KR/images
```
캡션 문구는 `StoreArt.java`의 `SHOTS`에서 바꿀 수 있습니다.

## 개인정보처리방침 페이지 올리기 (GitHub Pages)
1. `store/privacy/index.html`에서 `CONTACT_EMAIL` 두 곳을 실제 이메일로 바꿈
2. github.com에서 새 **Public** 저장소 만들기 (예: `gemtower-privacy`)
3. **Add file → Upload files**로 `index.html` 업로드 → Commit
4. **Settings → Pages → Branch: main / (root) → Save**
5. 1~2분 뒤 `https://<GitHub아이디>.github.io/gemtower-privacy/` 가 열리면 이 주소를 Play Console에 입력

GitHub 대신 Netlify Drop(app.netlify.com/drop)에 `privacy` 폴더를 끌어다 놓아도 바로 공개 URL이 생깁니다.
