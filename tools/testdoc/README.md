# Sinh tài liệu test theo mẫu SEP490

`gen_unittest_function.py` sinh **Report 5.1 - Unit Test (Function)** từ chính
mã nguồn test và kết quả chạy test, thay vì gõ tay 385 test case vào Excel.

## Cách chạy

```
ant test                                        # bắt buộc chạy trước
python tools/testdoc/gen_unittest_function.py
```

Kết quả mặc định: `%USERPROFILE%\Documents\POSCS_UnitTest_Function.xlsx`

File .xlsx là tài liệu nộp nên **để ngoài repo**. Muốn ghi chỗ khác:

```
python tools/testdoc/gen_unittest_function.py -o "D:\BaoCao\UnitTest.xlsx"
```

Script đọc `build/test/results/*.xml` để lấy trạng thái Passed/Failed/Untested
**thật**. Chưa chạy `ant test` thì script dừng - tài liệu không bao giờ ghi
"Passed" cho test chưa từng chạy. Chạy lại `ant test` rồi sinh lại mỗi khi sửa
code hoặc thêm test.

Trên máy chưa cài Ant vào PATH (xem `.github/workflows/tests.yml`):

```
"C:\Program Files\NetBeans-25\netbeans\extide\ant\bin\ant.bat" \
  -Dj2ee.platform.classpath="%CD%\lib-build\jakarta.servlet-api-6.1.jar" \
  -Dlibs.CopyLibs.classpath="%CD%\lib-build\org-netbeans-modules-java-j2seproject-copylibstask.jar" \
  test
```

## Cách script hiểu test

Tên method test trong repo theo quy ước `<method>_<điều kiện>_<kỳ vọng>`:

```
insert_noRowAffected_rollsBackAndReturnsMinusOne
       └── dòng Condition      └── dòng Confirm
```

Mỗi `@Test` thành một cột UTCID; mỗi điều kiện/kỳ vọng khác nhau thành một
dòng, đánh dấu `O` ở giao điểm. Cuối mỗi sheet có bảng đối chiếu
`UTCID -> tên method JUnit` để truy ngược về code.

Test đặt tên chỉ có hai đoạn (`url_javascriptSchemeRejected`) được tách tại từ
mở đầu phần kỳ vọng (`OUTCOME_WORDS`).

## Tiếng Việt

Toàn bộ nhãn, tiêu đề cột và sheet Hướng dẫn đã là tiếng Việt. Phần nội dung
(điều kiện / kỳ vọng của từng UTCID) được dịch qua `vi_glossary.json` — từ
điển ánh xạ chuỗi sinh từ tên method test sang tiếng Việt.

Thêm test mới mà chưa có bản dịch thì script vẫn chạy, giữ nguyên chuỗi tiếng
Anh và in ra cuối danh sách những chuỗi cần bổ sung, đã định dạng sẵn để dán
thẳng vào `vi_glossary.json`.

Tên lớp, tên hàm và tên sheet giữ nguyên tiếng Anh vì là định danh trong mã
nguồn.

## Hai chỗ cần người rà lại

1. **Cột `Type (N/A/B)`** là suy đoán từ tên test (danh sách từ khoá trong
   `BOUNDARY_WORDS` / `ABNORMAL_WORDS` / `REJECTION_WORDS`). Phân bố hiện tại
   N=145 / A=225 / B=15 - số ca biên thấp vì bộ test vốn ít ca biên, không
   phải do script đếm sai. Rà lại trước khi nộp.
2. **`ALIASES` và `WHOLE_CLASS`** ánh xạ tiền tố tên test sang method
   production. Controller là servlet điều phối theo `action` nên tiền tố test
   là tên action, phải khai báo tay. Thêm controller/handler mới thì bổ sung
   vào hai bảng này, nếu không sheet sẽ mang tên action thay vì tên handler.

## Phạm vi

Chỉ gồm unit test. Ba lớp trong `poscs.integration` bị loại ra
(`EXCLUDED_TEST_CLASSES`) vì thuộc Report 5.2.
