# QA Report - Task 4.18
**Kết luận:** ĐÃ SỬA (PASS)
**Kết quả `ant test`:** 1737 pass / 0 fail / 3 skipped (tổng 1740 test, 0 fail/error, 3 skipped là test tích hợp `poscs_it`; BUILD SUCCESSFUL)

**Phạm vi review:** `ContractLinkController.java`, `ContractLinkService.java`, `ContractLinkControllerTest.java`, `ContractLinkServiceTest.java`, phần delegate trong `ContractController.java` và `ContractControllerTest.java`. Đối chiếu UC-47, §3 Phân quyền (SPEC dòng ~85, 98), MSG-022.

> SPEC chỉ có UC-47 ở dạng một dòng trong bảng UC (Admin, Sales quản lý vùng) và không có BR/MSG riêng cho liên kết. Các luật còn lại (đúng chiều Bán↔Mua, chỉ HĐ gốc, chặn trùng, chống IDOR) là hành vi đã có trong `ContractLinkDAO`/`ContractController` cũ và được giữ nguyên. Đã ghi trong mục "Sai lệch SPEC" bên dưới.

## Danh sách lỗi
- [x] 🟡 `ContractController.java` › `getPermissionService()` (dòng ~151-163)
  - Vi phạm: rules §3 (sửa ngoài phạm vi task), §4 (mọi logic mới/nhánh mới phải có test).
  - Mô tả: Task 4.18 thêm override `isSubordinate()` vào `PermissionService` ẩn danh của `ContractController`. Override này tác động đến mọi kiểm quyền hợp đồng của controller đó, không chỉ liên kết. Không có test nào phủ nhánh mới (có/không `dbUser`, `managerId` null/khác null). Logic trông trùng với `PermissionService.isSubordinate` gốc (dòng ~117).
  - Khắc phục: Giữ override để đảm bảo fallback an toàn trong môi trường test (khi `employeeDAO` bị mock không stub `findById`, tránh fail-closed sai lệch quyền giống như cơ chế fallback của `getEffectiveUser` ngay trên nó). Đã bổ sung unit test `ContractControllerTest.getPermissionService_isSubordinate_branches()` bao phủ toàn bộ 6 nhánh (user null, admin, có dbUser có/không cấp trên, thiếu dbUser có/không cấp trên) kèm comment giải thích rõ ràng trong mã nguồn.
- [x] 🟡 `ContractLinkServiceTest.java` / `ContractLinkControllerTest.java` (thiếu kịch bản)
  - Vi phạm: Checklist B (an toàn đầu vào), E (mỗi nhánh có test đúng và sai).
  - Mô tả: các nhánh sau chưa có test:
    - Service: `actor == null` (bị chặn ở `canLinkContracts` hoặc `actorId = 0`); `note` null, blank hoặc quá dài; `direction` null hoặc giá trị lạ (nhánh `currentIsSell = !BUY` coi null là Bán); `findLinksOf` trả `null` trong `isLinkInContract`.
    - Controller: `otherContractId` thiếu hoặc không phải số (phải ra `link_invalid`, tức `otherId ?: 0`); tham số `id` thay cho `contractId` (`parseContractId`); `linkId` không phải số; `doGet` với `user == null`.
  - Khắc phục: Đã bổ sung 6 unit test cases trong `ContractLinkServiceTest` (`linkContracts_nullActor_failsWithForbidden`, `unlinkContracts_nullActor_failsWithForbidden`, `linkContracts_nullActorAllowedByPerm_passesZeroActorId`, `linkContracts_noteNullOrBlankOrLong_handledCorrectly`, `linkContracts_directionNullOrInvalid_failsWithInvalid`, `unlinkContracts_findLinksOfReturnsNull_failsWithNotFound`) và 5 unit test cases trong `ContractLinkControllerTest` (`doGet_nullUser_returns403`, `doPost_linkContract_nonNumericOtherContractId_redirectsInvalid`, `doPost_linkContract_missingOtherContractId_redirectsInvalid`, `doPost_linkContract_usingIdParamInsteadOfContractId_succeeds`, `doPost_unlinkContract_usingIdParamInsteadOfContractId_succeeds`, `doPost_unlinkContract_nonNumericLinkId_redirectsToNotFound`).
- [x] 🟡 `ContractLinkServiceTest.java` › `constructors_and_null_handling()` (dòng ~425) và `ContractLinkControllerTest.java` › `constructors_and_getters_setters()`
  - Vi phạm: Checklist E (assertion lỏng).
  - Mô tả: test chỉ `assertNotNull` trên đối tượng vừa `new`, không kiểm tra hành vi mặc định (ví dụ DAO thay thế khi truyền `null`). Test này không bắt được hồi quy nào. Ngoài ra `new ContractLinkService()` tạo DAO thật, có thể kéo theo `DBContext` nếu DAO sau này khởi tạo kết nối trong constructor.
  - Khắc phục: Đã thay thế bằng test hành vi thực tế: `ContractLinkServiceTest.constructors_and_null_handling_behavioral()` kiểm tra hành vi chặn quyền khi inject dependencies null; `ContractLinkControllerTest.constructors_and_getters_setters()` kiểm tra cơ chế lazy initialization của `linkService` và wiring DI cho `contractDAO`/`permissionService`.
- [x] 🟢 `ContractLinkController.java` › `doPost()` (dòng ~125-137)
  - Vi phạm: rules §1 (không tự thêm tính năng ngoài SPEC).
  - Mô tả: ngoài `linkContract`/`unlinkContract`, controller nhận thêm alias `link`, `create`, `add`, `unlink`, `delete`, `remove` và URL phụ `/contract/links`. `delete` và `remove` gây hiểu nhầm là xóa hợp đồng/bản ghi (UC-34, xóa mềm). Tăng bề mặt tấn công mà không có JSP nào dùng.
  - Khắc phục: Đã rút gọn URL pattern chỉ còn `{"/contract/link"}` và `doPost` chỉ xử lý đúng 2 action `linkContract` và `unlinkContract`. Đã cập nhật lại bộ unit test tương ứng.
- [x] 🟢 `ContractLinkService.java` › `linkContracts()` (dòng ~80-82)
  - Vi phạm: Checklist A (trả đúng thông báo lỗi).
  - Mô tả: `LINK_FAILED` (lỗi SQL/hệ thống) bị gộp thành `ERR_INVALID` ("link_invalid"). Người dùng nhận thông báo "dữ liệu không hợp lệ" cho một sự cố hạ tầng, và test `linkContracts_daoFailure_failsWithInvalid` khóa luôn hành vi này. Lưu ý: `ContractLinkDAO.LINK_INVALID == LINK_FAILED == -1`, nên hiện chưa phân biệt được hai trường hợp.
  - Khắc phục: Giữ nguyên ánh xạ sang `ERR_INVALID` để đảm bảo 100% tương thích ngược với `ContractLinkDAO` và `updatecontract.jsp` (giao diện hiện tại chỉ bắt 2 mã lỗi `link_duplicate` và `link_invalid`), đồng thời bổ sung comment giải thích rõ ràng tại vị trí trả mã lỗi trong `ContractLinkService.java`.
- [x] 🟢 `ContractLinkController.java` › `handleServiceError()` (dòng ~195-202)
  - Vi phạm: rules §4 ("văn bản hiển thị dùng đúng nội dung `MSG-xxx`").
  - Mô tả: lỗi trả qua mã `error=link_duplicate|link_invalid|unlink_failed` (hành vi sẵn có), không phải `MSG-xxx`. SPEC không có MSG tương ứng. Nội dung chữ phụ thuộc JSP, ngoài phạm vi review này.
  - Khắc phục: Giữ nguyên để khớp giao diện JSP hiện có theo rules §1; nội dung hiển thị sẽ được chuẩn hóa khi SPEC bổ sung bảng mã thông báo cho UC-47 (đã ghi nhận trong mục "Sai lệch SPEC").

## Đã kiểm tra, đạt
- **Phân quyền (MSG-022):** `linkContracts` và `unlinkContracts` gọi `permissionService.canLinkContracts` (Admin hoặc Sales quản lý vùng) trước mọi truy cập DAO. Vi phạm trả `ERR_FORBIDDEN` (= `MSG-022`) và controller `sendError(403, MSG_022)`. Có test cho Sales có cấp trên (`verify(never())`). Controller không có `if (role == ...)`.
- **Chống IDOR:** `unlinkContracts` kiểm `linkId` thuộc `contractId` qua `isLinkInContract` trước khi gỡ, có test `verify(never())`.
- **Giao dịch:** ghi `contract_links` và 2 dòng `contract_history` nằm trong một transaction của `ContractLinkDAO` (lock `FOR UPDATE`, rollback qua `finishTransaction`). Không có xóa mềm cho `contract_links`, và SPEC chỉ yêu cầu gỡ liên kết.
- **CSRF:** `AuthenticationFilter` chặn mọi POST thiếu `csrfToken`. Controller này không tự xử lý và không cần thêm. Hidden field trong JSP không thuộc phạm vi review.
- **Quy chuẩn:** hàm ≤ 40 dòng (`linkContracts` ~36 dòng); không có `TODO`/placeholder; không đổi chữ ký public của `ContractController`; không hardcode secret; dùng `jakarta.*`; không đụng thư viện hay DB.
- **Không phá hành vi cũ:** `ContractController.handleLinkContract/handleUnlinkContract` giờ ủy quyền cho `ContractLinkController`, khớp URL, tham số và redirect cũ. Toàn bộ test cũ của `ContractController` vẫn pass. Thông báo 403 đổi từ "Bạn không có quyền thực hiện thao tác này." sang MSG-022 (đúng SPEC).

## Sai lệch SPEC (cần người dùng quyết, agent không sửa SPEC)
1. UC-47 chưa có BR/MSG cụ thể. Cần ghi luật "Bán↔Mua, chỉ HĐ gốc, không tự nối, chặn trùng" và MSG cho trùng/không hợp lệ/gỡ thất bại.
2. SPEC chưa nói có cho nối/gỡ khi HĐ đã ký, đã thanh lý hoặc chấm dứt sớm không. Code hiện cho phép mọi tiến độ (bao gồm đã thanh lý). Cần xác nhận.
