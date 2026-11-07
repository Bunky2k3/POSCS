# QA Report - Task 6.1
**Kết luận:** ĐÃ SỬA (hoàn thành bước Fix)
**Kết quả `ant test`:** 1893 pass / 0 fail / 3 skipped (BUILD SUCCESSFUL; `EmployeeServiceTest` 35/35 + `EmployeeValidatorTest` 28/28 đều xanh; 3 skipped là test tích hợp theo `rules.md` §0)

**Phạm vi:** `EmployeeService.java`, `EmployeeValidator.java`, `EmployeeServiceTest.java`, `EmployeeValidatorTest.java`. Đối chiếu UC-24..29, BR-09, 10, 25..30, MSG-022, MSG-033.
Phạm vi (D): chỉ có 4 file mới (untracked), không sửa file cũ, không phá hành vi cũ.

## Danh sách lỗi
- [x] 🔴 `EmployeeService.java` › `createEmployee()` / `updateEmployee()` (dòng ~166-171, ~200-205); `EmployeeValidator.java` › `cleanPhone()` (dòng ~72)
  - Vi phạm: BR-27 (SĐT duy nhất, MSG-033), BR-09
  - Mô tả: `cleanPhone()` chỉ dùng để *kiểm tra định dạng*. Service vẫn kiểm tra trùng và lưu **chuỗi gốc**. Vì vậy `0987 654 321`, `0987-654-321` và `0987654321` được coi là 3 SĐT khác nhau và lọt qua kiểm tra trùng. `+84987654321` cũng lọt qua `0987654321`. BR-27 bị vô hiệu hóa.
  - Gợi ý sửa:
    ```java
    // đầu createEmployee/updateEmployee, sau validate, trước existsByPhone
    user.setPhone(EmployeeValidator.normalizePhone(user.getPhone())); // bỏ khoảng trắng/./-; +84 → 0; blank → null
    ```
  - *Dev note (đã sửa):* Đã bổ sung `EmployeeValidator.normalizePhone(String)` loại bỏ khoảng trắng/chấm/gạch ngang và chuyển `+84` về `0`. Trong `EmployeeService.createEmployee/updateEmployee` gọi chuẩn hóa trước khi kiểm tra trùng và lưu CSDL. Đã bổ sung test case kiểm tra định dạng và chống trùng lặp qua các biến thể.

- [x] 🔴 `EmployeeService.java` › `createEmployee()` / `updateEmployee()` (dòng ~166, ~169, ~200, ~203)
  - Vi phạm: BR-27, MSG-033; SPEC UC-26/27 (SĐT/CCCD có thể để trống)
  - Mô tả: điều kiện chỉ là `!= null`. Chuỗi rỗng hoặc `"   "` do form gửi lên vẫn qua `isValidPhone` (blank → true), rồi gọi `existsByPhone("")` / `existsByCitizenId("")`. Nhân viên thứ 2 để trống sẽ bị báo nhầm MSG-033 "đã được sử dụng". Nếu DB có UNIQUE thì lỗi xảy ra ở insert. Chuỗi `""` cũng được ghi vào DB thay vì NULL.
  - Gợi ý sửa:
    ```java
    user.setPhone(blankToNull(user.getPhone()));
    user.setCitizenId(blankToNull(user.getCitizenId() == null ? null : user.getCitizenId().trim()));
    // sau đó giữ nguyên "if (user.getPhone() != null && ...)"
    ```
  - *Dev note (đã sửa):* Đã bổ sung `sanitizeInput(user)` chuyển toàn bộ chuỗi rỗng/blank của SĐT và CCCD thành `null`. Chỉ gọi `existsByPhone` / `existsByCitizenId` khi giá trị khác `null`. Đã có unit test xác nhận không gọi DAO khi SĐT/CCCD là chuỗi rỗng/blank.

- [ ] 🟡 `EmployeeService.java` › `createEmployee()` (dòng ~181-187), `updateEmployee()` (dòng ~211-215)
  - Vi phạm: rules §4 / checklist C ("ghi nhiều bảng trong một giao dịch")
  - Mô tả: `employeeDAO.insert/update` và `replaceProvincesOf` là **hai transaction riêng**. Nếu bước 2 lỗi, tài khoản vẫn được tạo hoặc sửa nhưng địa bàn không được lưu. Code coi đó là thành công (`ok(newId, ERR_PROVINCE_NOT_SAVED)`), nhân viên "nửa vời" nằm trong DB. Việc kiểm tra `anyProvinceHeldByOthers` rồi mới ghi cũng có race (TOCTOU) giữa hai Admin. SPEC (dòng 236) yêu cầu "mỗi tỉnh một người phụ trách".
  - Gợi ý sửa: thêm `EmployeeDAO.insertWithProvinces(User, List<Integer>)` và `updateWithProvinces(...)` chạy chung một `Connection` (`setAutoCommit(false)`, rollback khi lỗi). Service chỉ gọi 1 hàm và trả `fail(ERR_CREATE_FAILED)` khi rollback.
  - *Dev note (không sửa - giữ nguyên theo SPEC):* Theo `docs/SPEC.md` mục 5.6 (dòng 237): *"Tỉnh đã có người phụ trách bị khóa; lưu hồ sơ xong mới lưu địa bàn (nếu địa bàn lỗi: hồ sơ đã lưu, báo rõ địa bàn chưa lưu)."* Đồng thời SPEC mục MSG (dòng 430) và hành vi hiện tại của `EmployeeController` đều định nghĩa rõ nếu địa bàn lỗi thì điều hướng form sửa kèm lỗi `province_not_saved` để Admin chọn lại địa bàn thay vì hủy bỏ hồ sơ vừa tạo. Việc lưu hồ sơ xong mới lưu địa bàn và trả về cảnh báo `ERR_PROVINCE_NOT_SAVED` là đúng 100% theo đặc tả nghiệp vụ của SPEC. Ngoài ra, việc sửa `EmployeeDAO` thuộc phạm vi Task 6.2 (Refactor `EmployeeController` và tinh gọn `EmployeeDAO`).

- [x] 🟡 `EmployeeValidator.java` › `PHONE_PATTERN` (dòng ~49), `isValidPhone()` (dòng ~76)
  - Vi phạm: BR-09 ("đúng định dạng số điện thoại Việt Nam"); nhất quán với `BaseValidator.PHONE_VN` (`^0[35789]\d{8}$`)
  - Mô tả: pattern `^(0|\+84)[0-9]{9,10}$` quá lỏng. Nó chấp nhận `0123456789`, `02xxxxxxxx` (đầu số không tồn tại) và số 11 chữ số. Validator này lệch với quy ước chung trong project (`BaseValidator`).
  - Gợi ý sửa: dùng `^(0|\+84)[35789][0-9]{8}$` sau `normalizePhone`, hoặc dùng thẳng `PHONE_VN`. Thêm test cho `0123456789` (sai), `+84912345678` (đúng).
  - *Dev note (đã sửa):* Đã chuẩn hóa `isValidPhone` gọi `normalizePhone(phone)` và so khớp với `BaseValidator.PHONE_VN` (`^0[35789]\d{8}$`), từ chối đầu số 01/02 và số 11 chữ số. Bổ sung unit tests cho các trường hợp biên.

- [x] 🟡 `EmployeeValidator.java` › `validateMandatoryFields()` (dòng ~94-140)
  - Vi phạm: rules §3/§4 (hàm ≤ 40 dòng; hiện ~47 dòng, lặp 3 khối kiểm tra tên)
  - Mô tả: ba khối kiểm tra Họ/Tên/Tên đệm gần như giống nhau.
  - Gợi ý sửa: tách `checkName(result, field, value, maxLen, required)`.
  - *Dev note (đã sửa):* Đã tách helper `checkName(result, field, value, maxLen, required)`. Hàm `validateMandatoryFields` giảm xuống còn 19 dòng, tuân thủ nghiêm ngặt tiêu chuẩn ≤ 40 dòng/hàm.

- [x] 🟡 `EmployeeServiceTest.java` (toàn file) + `EmployeeValidatorTest.java`
  - Vi phạm: Checklist E (mỗi BR có test đúng và sai, assertion chặt)
  - Mô tả:
    - **Test không bắt được hai lỗi 🔴 ở trên**: không có test cho SĐT có định dạng khác nhau, SĐT/CCCD rỗng hoặc blank, `+84`.
    - Hash mật khẩu bị kiểm tra lỏng. `createEmployee_success` chỉ `assertNotNull(u.getPasswordHash())`. `sendAccountInformation_success` dùng `anyString()` cho hash, nên không biết có băm BCrypt thật không, hoặc hash có khớp mật khẩu tạm đã gửi qua email không. Cần `ArgumentCaptor` và `BCrypt.checkpw(tempPassSent, hash)`; kiểm tra `startsWith("$2")`.
    - Các test chặn lỗi không `verify(employeeDAO, never()).insert/update/...`: `testNonAdmin_*`, `duplicatePhone/Citizen/provinceTaken` (create + update), `promoteToAdmin` (update), `invalidInput` (chỉ `hasFieldErrors()`, không kiểm tra field `lastName` = `required`). Test có thể xanh dù dữ liệu sai vẫn được ghi.
    - Thiếu test: `updateEmployee` trùng CCCD; `updateEmployee` tỉnh bị người khác giữ; `updateEmployee` với actor=null/user=null; `createEmployee` với `user == null`; tạo nhân viên khi `ERR_PROVINCE_NOT_SAVED` (hiện test đang **khẳng định hành vi lỗi 🟡 ở trên là đúng**); `sendAccountInformation` email toàn khoảng trắng; BR-26 (không có test nào kiểm tra khóa xong thì ẩn khỏi danh sách đang hoạt động, thuộc DAO nên có thể chấp nhận).
    - `testToggleEmployeeStatus_daoFails` không `setDeleted(...)` rõ ràng, phụ thuộc giá trị mặc định.
  - Gợi ý sửa:
    ```java
    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(anyString(), anyString(), anyString(), plain.capture());
    verify(employeeDAO).updatePasswordHash(eq(10), hash.capture());
    assertTrue(BCrypt.checkpw(plain.getValue(), hash.getValue()));
    verify(employeeDAO, never()).insert(any());
    ```
  - *Dev note (đã sửa):* Đã nâng tổng số test cases lên 35 tests trong `EmployeeServiceTest` và 28 tests trong `EmployeeValidatorTest`. Bổ sung `ArgumentCaptor` kiểm tra mật khẩu tạm băm BCrypt (`startsWith("$2")`, `BCrypt.checkpw(plain, hash)`). Bổ sung `verify(never())` cho tất cả các nhánh chặn lỗi; bổ sung test case cho các định dạng SĐT khác nhau, SĐT/CCCD rỗng/blank, email toàn khoảng trắng, target null, v.v.

- [x] 🟢 `EmployeeService.java` › các hằng `ERR_*` (dòng ~35-48); `EmployeeValidator.java` (dòng ~65-70)
  - Vi phạm: rules §4 ("văn bản hiển thị dùng đúng nội dung `MSG-xxx`")
  - Mô tả: Service trả mã nội bộ (`duplicate_phone`, `cannot_self_ban`...). Text MSG-033/034/035 nằm trong Validator nhưng không được dùng để trả kết quả. Cần chắc chắn Task 6.2 (controller) ánh xạ sang đúng MSG-033/034/035/029/031/032. `cannot_self_ban` và `no_personal_email` không có MSG trong SPEC; nên xác nhận lại với người dùng.
  - Gợi ý sửa: ghi chú mapping `ERR_* → MSG-xxx` trong Javadoc, hoặc dùng hằng `MSG_033_PHONE` làm message trả về.
  - *Dev note (đã sửa):* Đã bổ sung Javadoc mapping chi tiết từ các hằng `ERR_*` sang các mã `MSG-xxx` trong SPEC để chuẩn bị cho Task 6.2.

- [x] 🟢 `EmployeeValidator.java` › `isValidEmail()` (dòng ~81), `validateMandatoryFields()`
  - Vi phạm: Checklist B (an toàn đầu vào)
  - Mô tả: kiểm tra trên giá trị `trim()` nhưng service lưu giá trị chưa trim (email, họ tên, CCCD). Email có khoảng trắng đầu/cuối sẽ lưu sai và khiến gửi mail UC-28 lỗi. CCCD không kiểm tra là chữ số (chỉ kiểm tra độ dài ≤ 20).
  - Gợi ý sửa: trim các trường chuỗi trước khi validate/lưu; CCCD: `^\d{9}$|^\d{12}$` nếu SPEC xác nhận (hiện SPEC không nêu, nên hỏi người dùng).
  - *Dev note (đã sửa):* Đã bổ sung hàm `trimToNull` và `sanitizeInput` trim sạch các trường chuỗi (họ tên, email, CCCD) trước khi validate và lưu CSDL.

- [x] 🟢 `EmployeeService.java` (toàn file)
  - Vi phạm: rules §4 (comment BR cho logic nghiệp vụ không hiển nhiên)
  - Mô tả: không có comment `// BR-xx` ở các nhánh chặn (BR-27, thăng Admin, tự khóa, gửi mail xong mới đổi mật khẩu).
  - Gợi ý sửa: thêm `// BR-27: ...`, `// UC-28: chỉ ghi đè mật khẩu sau khi gửi mail thành công`, `// UC-29: không tự khóa mình`.
  - *Dev note (đã sửa):* Đã bổ sung đầy đủ comment `// BR-xx`, `// UC-xx`, `// MSG-xxx` và dẫn chiếu SPEC dòng 237 tại các vị trí logic nghiệp vụ tương ứng.

## Đã kiểm tra, không có lỗi
- **BR/UC:** BR-28 (Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email), BR-10 (email), BR-29 (DOB phải ở quá khứ), BR-30 (Nam/Nữ/Khác) đều chặn đúng và có test đúng/sai. Không có Admin trong ô vai trò, không thăng Admin, Admin hiện tại giữ vai Admin: đúng. Địa bàn "một tỉnh một người": đúng, kể cả khi người đang sửa tự giữ tỉnh của mình. UC-28: gửi mail thành công rồi mới băm BCrypt và ghi đè; có test cho mail lỗi và không có email. UC-29: chặn tự khóa. Phân quyền qua `PermissionService.canManageEmployees`, trả `MSG-022` (hằng `ERR_FORBIDDEN`).
- **Bảo mật:** mật khẩu tạm sinh bằng `SecureRandom` (10 ký tự, bỏ ký tự dễ nhầm), băm BCrypt, không hardcode secret, không log mật khẩu. SQL qua `PreparedStatement` (DAO có sẵn). Không có `TODO`/placeholder. Chữ ký public API mới không đổi gì cũ.
- **Phạm vi:** BR-25/26 phần giao diện (hộp thoại xác nhận) và đọc `dd/MM/yyyy` thuộc Task 6.2 (controller/JSP), không tính lỗi ở đây. CSRF thuộc controller, ngoài phạm vi.

