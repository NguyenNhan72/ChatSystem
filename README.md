# Mobile Chat System — Chapter 12 (Alex Xu)

Dự án thiết kế và hiện thực hệ thống trò chuyện thời gian thực (Real-time Mobile Chat System) trên nền tảng di động Android và Backend Kotlin Ktor, dựa trên kiến trúc chuẩn tại **Chapter 12: Design a Chat System** trong cuốn sách *System Design Interview – An Insider's Guide* của tác giả Alex Xu.

---

## Cấu trúc Repository

Dự án được phân chia rõ ràng thành 2 thư mục chính:

```
SysDesign_ChatSystem/
├── SourceCode/           # Toàn bộ mã nguồn dự án (Android, Server, Database, Gradle)
│   ├── .idea/            # Cấu hình dự án Android Studio
│   ├── app/              # Module Android Client (Kotlin · Jetpack Compose · MVVM · Room)
│   ├── server/           # Module Ktor Server (Netty · REST API · WebSocket · HikariCP)
│   ├── shared/           # Module Kotlin Multiplatform (Model JSON · WebSocket Protocol)
│   ├── gradle/           # Gradle Wrapper & Version Catalog
│   ├── scripts/          # Các script tiện ích hỗ trợ demo và test
│   ├── build.gradle.kts  # Gradle build configuration
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── docker-compose.yml# PostgreSQL 16
│   └── README.md         # Hướng dẫn chi tiết cách build, cấu hình và chạy mã nguồn
│
├── TaiLieu/              # Toàn bộ tài liệu kỹ thuật, báo cáo phân tích và bài thuyết trình
│   ├── docs/             # 8 tài liệu phân tích chi tiết (Kiến trúc, API, WebSocket, Test, Q&A...)
│   │   └── screenshots/  # Hơn 30 ảnh thực nghiệm kết quả kiểm thử hệ thống
│   ├── slides/           # File slide thuyết trình PowerPoint (.pptx)
│   └── README.md         # Mục lục chi tiết tra cứu tài liệu và slide
│
├── .gitignore            # Git ignore tổng hợp cho toàn bộ repository
└── README.md             # Giới thiệu tổng quan dự án (file này)
```

---

## Hướng dẫn xem & Khởi chạy nhanh

### 1. Xem tài liệu & Báo cáo đồ án
Truy cập vào thư mục **[TaiLieu/](TaiLieu/README.md)**:
- [01. Phân tích yêu cầu & Kiến trúc hệ thống](TaiLieu/docs/01-phan-tich-va-kien-truc.md)
- [02. Đặc tả REST API](TaiLieu/docs/02-api.md)
- [03. Giao thức WebSocket & Real-time](TaiLieu/docs/03-websocket.md)
- [04. Ứng dụng Android Client & Mô hình Outbox](TaiLieu/docs/04-android.md)
- [05. Báo cáo kết quả kiểm thử (Test Report)](TaiLieu/docs/05-test-report.md)
- [06. Kịch bản Demo 5 phút](TaiLieu/docs/06-demo.md)
- [09. Hướng dẫn chạy app & bảng demo (mới nhất)](TaiLieu/docs/09-huong-dan-chay-va-demo.md)
- [10. Lời thuyết trình & bộ câu hỏi vấn đáp](TaiLieu/docs/10-thuyet-trinh-va-van-dap.md)
- [07. Bộ câu hỏi phản biện bảo vệ đồ án (Q&A)](TaiLieu/docs/07-phan-bien.md)
- [Slide thuyết trình PowerPoint](TaiLieu/slides/)

### 2. Mở & Chạy Mã nguồn (Source Code)
Mở thư mục **`SourceCode/`** trực tiếp bằng **Android Studio**.

Chi tiết xem tại **[SourceCode/README.md](SourceCode/README.md)**:
1. **Chạy Database (PostgreSQL 16):**
   ```bash
   cd SourceCode
   docker compose up -d
   ```
2. **Khởi động Backend Server (Ktor):**
   ```bash
   ./gradlew :server:run
   ```
3. **Chạy ứng dụng Android:**
   Chọn emulator trong Android Studio và nhấn **Run** (hoặc `./gradlew :app:installDebug`).
