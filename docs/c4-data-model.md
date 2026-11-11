# pocketMix — C-4 데이터 형태

## 기본 원칙

영상 파일은 파일 저장 공간에 두고, DB에는 영상 정보와 저장 상태를 기록한다.
각 영상은 고유한 videoId로 구별한다. 첫 버전에서는 영상 하나를 Pocket 하나에만 연다.

## 1. Video — 보관함의 영상 정보

| 항목 | 형태 | 의미 |
|---|---|---|
| videoId | 문자열 | 영상을 구별하는 고유 ID |
| title | 문자열 | 보관함과 Pocket에 표시할 제목 |
| fileLocation | 문자열 | 실제 영상 파일을 찾을 위치 |
| durationMs | 정수 | 영상 전체 길이, 밀리초 단위 |

## 2. PlaybackState — 영상별 시청 상태

| 항목 | 형태 | 의미 |
|---|---|---|
| videoId | 문자열 | Video와 연결되는 ID |
| positionMs | 정수 | 마지막 시청 위치 |
| volume | 정수 | 영상별 음량, 0~100 |
| muted | 참/거짓 | 음소거 여부 |

음소거해도 volume 값은 보존한다. 음소거를 해제하면 이전 음량으로 돌아간다.
저장된 시청 위치가 영상 길이보다 크면 영상 길이를 넘지 않게 조정한다.

## 3. PocketState — 창의 배치와 표시 상태

| 항목 | 형태 | 의미 |
|---|---|---|
| videoId | 문자열 | Pocket에서 보여줄 영상의 ID |
| x, y | 0~1 사이 숫자 | 작업 공간에서 창의 가로·세로 위치 비율 |
| width, height | 0~1 사이 숫자 | 작업 공간에 대한 창의 너비·높이 비율 |
| order | 정수 | 창이 겹쳤을 때 앞뒤 순서. 큰 값이 앞쪽 |
| status | 문자열 | open, minimized, closed 중 하나 |

open은 화면에 열린 상태, minimized는 아래 목록에 접힌 상태,
closed는 화면에서 사라졌지만 이전 설정을 보존한 상태다.
화면 크기가 바뀌면 창이 화면 밖으로 벗어나지 않게 위치와 크기를 조정한다.

## 4. 서로 다른 영상 두 개의 예시

아래 값과 파일 경로는 실제 DB 데이터가 아닌 가상 예시다.

### Video

| videoId | title | fileLocation | durationMs |
|---|---|---|---:|
| sea-001 | 오후의 해변 | sample/sea.mp4 | 380000 |
| city-002 | 도시의 저녁 | sample/city.mp4 | 252000 |

### PlaybackState

| videoId | positionMs | volume | muted |
|---|---:|---:|---|
| sea-001 | 90000 | 70 | false |
| city-002 | 45000 | 25 | false |

해변 영상은 1분 30초, 도시 영상은 45초 위치에 저장되어 있다.
해변 영상의 음량을 바꿔도 도시 영상의 음량은 25로 유지된다.

### PocketState

| videoId | x | y | width | height | order | status |
|---|---:|---:|---:|---:|---:|---|
| sea-001 | 0.05 | 0.08 | 0.85 | 0.38 | 1 | open |
| city-002 | 0.10 | 0.52 | 0.85 | 0.38 | 2 | open |

두 Pocket은 서로 다른 위치에 열려 있고 도시 영상의 창이 앞쪽이다.
도시 영상의 status를 minimized로 바꾸면 해당 Pocket만 아래 목록으로 접힌다.
status를 closed로 바꿔도 영상의 시청 위치·음량과 마지막 창 설정은 남는다.