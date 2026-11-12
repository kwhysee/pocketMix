# pocketMix

pocketMix는 여러 영상을 각각의 Pocket 창에서 동시에 재생하는 Android 앱입니다. 사용자는 창의 위치와 크기를 바꾸고, 영상마다 재생 위치·음량·음소거를 따로 조절할 수 있습니다. 앱을 다시 열면 이전 작업 공간을 일시정지 상태로 복원합니다.

## 만들게 된 이유

컴퓨터에서 여러 창을 배치하듯 모바일 화면에서도 여러 영상을 함께 보고 조작할 수 있는지 검증하기 위해 만들었습니다. 첫 버전은 pocketMix 앱 안에 저장된 영상을 대상으로 합니다.

## 현재 구현된 기능

- 두 개 이상의 영상을 별도 Pocket에서 동시에 재생
- Pocket별 재생·일시정지·탐색·음량·음소거
- Pocket 이동·크기 변경·앞으로 가져오기·최소화·닫기
- 전체 일시정지와 열린 Pocket 정렬
- MP4 파일을 선택해 앱 보관함으로 가져오기
- 영상별 시청 위치와 Pocket 배치를 SQLite에 저장하고 복원
- DB를 읽는 동안 로딩 화면 표시, 읽기 실패 시 재시도
- 영상별 재생 오류 표시와 재시도

## 사용 흐름

1. 보관함에서 샘플 영상을 선택하거나 **영상 가져오기**로 MP4를 추가합니다.
2. 첫 영상을 Pocket에 담고, **영상 추가**로 두 번째 영상을 엽니다.
3. 두 영상을 각각 재생하며 창 배치와 소리를 조절합니다.
4. 앱을 벗어났다가 다시 열면 저장된 작업 공간이 복원됩니다. 영상은 자동 재생되지 않습니다.

같은 영상을 다시 선택하면 Pocket이 중복 생성되지 않고 기존 Pocket을 엽니다. Pocket을 닫아도 시청 위치와 음량 설정은 보존됩니다.

## 데이터 구성

| 데이터 | 저장 위치 | 내용 |
|---|---|---|
| 앱에 포함된 샘플 MP4 | `app/src/main/assets/videos/` | 첫 실행에서 사용할 영상 파일 2개 |
| 사용자가 가져온 MP4 | 앱 내부 `files/videos/` | 파일 선택기로 가져온 영상의 복사본 |
| `videos` 테이블 | 로컬 SQLite `pocketmix.db` | 영상 ID, 제목, 파일 위치, 길이 |
| `playback_states` 테이블 | 로컬 SQLite `pocketmix.db` | 영상별 시청 위치, 음량, 음소거 |
| `pocket_states` 테이블 | 로컬 SQLite `pocketmix.db` | 창 위치·크기·순서와 열림 상태 |

영상 파일 자체를 SQLite 행에 넣지는 않습니다. DB의 `video_id`로 영상 정보와 두 종류의 상태를 연결합니다. 시청 위치는 일시정지·탐색·앱 이탈 시점과 재생 중 15초마다 갱신합니다.

## 기술 구성

- Kotlin, Jetpack Compose
- Media3 ExoPlayer: Pocket별 독립 플레이어
- Android SQLiteOpenHelper: 로컬 영상 목록과 상태 저장
- Android 시스템 파일 선택기: 사용자 MP4 선택

## 실행 방법

Android Studio에서 이 저장소를 연 뒤 Android 에뮬레이터를 실행합니다. 현재 프로젝트의 최소 Android API는 26입니다.

Windows의 Git Bash에서 Android Studio에 포함된 JDK를 사용하는 예시:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug
```

첫 명령은 디버그 APK를 만들고, 두 번째 명령은 연결된 에뮬레이터에 설치합니다. 다음 명령으로 앱을 엽니다. Android SDK가 기본 사용자 폴더에 설치된 Windows Git Bash 환경을 기준으로 합니다.

```bash
"$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n com.kwhysee.pocketmix/.MainActivity
```

## 확인한 동작

Android API 35 에뮬레이터에서 두 영상의 독립 재생·소리 조절, Pocket 이동·최소화, 앱 재시작 후 복원, MP4 가져오기를 확인했습니다. Database Inspector에서 `videos`, `playback_states`, `pocket_states`의 저장값도 확인했습니다.

실제 휴대전화에서의 동작은 아직 검증하지 않았습니다. 재생 가능한 영상 형식과 동시 재생 성능은 기기의 코덱 지원과 성능에 따라 달라질 수 있습니다. 사용자가 가져온 영상과 로컬 DB는 해당 앱의 데이터로 저장되며, 앱 데이터를 삭제하거나 앱을 제거하면 사라집니다.

## 설계 문서와 샘플 영상

- [핵심 사용 장면](docs/c1-core-scenarios.md)
- [화면 흐름](docs/c2-screen-flow.md)
- [데이터 흐름](docs/c3-data-flow.md)
- [데이터 형태](docs/c4-data-model.md)
- [첫 버전 완료 기준](docs/c5-acceptance.md)
- [샘플 영상 출처와 사용 조건](docs/sample-video-sources.md)