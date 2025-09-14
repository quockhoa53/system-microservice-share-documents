# system-microservice-share-documents
**Tền đề tài:** Xây dựng hệ thống chia sẻ tài liệu an toàn có kiểm soát bằng kiến trúc vi dịch vụ và GnuPG

**Nội dung nghiên cứu lý thuyết:**

Kiến trúc Microservices với Spring Boot, RESTful API, MySQL.
GnuPG / OpenPGP.js: Mã hóa end-to-end bằng public/private key, Ký số bằng private key để xác thực người gửi, Người nhận dùng public key của người gửi để kiểm chứng.
Keycloak: quản lý tài khoản, nhóm, quyền truy cập (RBAC).
Apache PDFBox / PyPDF2: nhúng watermark (ID người dùng, thời gian tải) để chống chia sẻ bản plaintext.
Audit Log Service: ghi nhận truy cập (ai tải, ai xem) vào cơ sở dữ liệu quan hệ (MySQL/Postgres).

**Nội dung nghiên cứu thực hành:**

a) Xây dựng hệ thống theo kiến trúc Microservices:

- User Service (người dùng và nhóm):
Đăng ký/đăng nhập.
Quản lý nhóm (giống mạng xã hội: nhiều nhóm tài liệu).
Cấp phát cặp khóa (public/private key) cho từng user.
- Document Service (dịch vụ tài liệu)
Upload tài liệu: Người gửi ký số file bằng private key, thêm watermark (UserID, timestamp) vào bản plaintext trước khi mã hóa, hệ thống mã hóa file bằng public key của từng người nhận.
Lưu trữ: mỗi bản tài liệu gắn với danh sách người nhận, quyền truy cập.
Tải xuống: Hệ thống kiểm tra chữ ký số để xác thực người gửi, người nhận giải mã bằng private key của mình.
- Dịch vụ xác thực & chống chia sẻ lại
Bản mã hóa ràng buộc với private key của từng user
Bản plaintext có watermark
- Audit Log Service (giám sát hệ thống)
Ghi lại tất cả hoạt động (upload, download, giải mã, xác thực).
Có thể truy vấn log để phát hiện bất thường.

b) Thiết kế và xây dựng giao diện web cho hệ thống có các chức năng:
Cho phép tạo nhóm, upload tài liệu, xem & tải xuống, tra cứu log/cá nhân.
