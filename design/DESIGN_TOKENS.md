# Design Tokens

コード上の基準は `core/designsystem/theme` に置く。

- Colors: Navy 950/900、Blue Black、Gray 800、Cyan、Violet、Emerald、Amber、Error
- Typography: display、headline、title、body、label の役割を固定
- Spacing: 4 / 8 / 12 / 16 / 24 / 32 / 48 dp
- Shapes: 8 / 12 / 18 / 26 / 34 dp の角丸階層
- Elevation: resting / floating / prominent
- Glow: subtle / active / victory
- Motion: quick 140 ms / standard 300 ms / expressive 520 ms

画面は標準色、角丸、余白、影、発光、時間、イージングを直接定義しない。進捗リングの 220 dp は情報階層を作る画面固有寸法、ボタンの 56 dp は操作性を確保するコンポーネント固有寸法として扱う。
