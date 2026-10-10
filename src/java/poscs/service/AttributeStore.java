package poscs.service;

import java.util.Map;

/**
 * Kho thuộc tính kiểu session, không phụ thuộc Servlet API. Controller bọc
 * {@code HttpSession} bằng interface này; test dùng {@link #of(Map)}.
 */
public interface AttributeStore {

    Object get(String key);

    void set(String key, Object value);

    void remove(String key);

    /** Bọc một Map thông thường (dùng cho test). */
    static AttributeStore of(Map<String, Object> map) {
        return new AttributeStore() {
            @Override
            public Object get(String key) {
                return map.get(key);
            }

            @Override
            public void set(String key, Object value) {
                map.put(key, value);
            }

            @Override
            public void remove(String key) {
                map.remove(key);
            }
        };
    }
}
