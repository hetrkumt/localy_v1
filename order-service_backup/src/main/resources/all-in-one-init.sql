-- =========================================================================
-- 1. 기존 주문 관련 테이블 삭제 (초기화)
--    order_line_items가 orders를 참조하므로, 의존성 순서 또는 CASCADE 사용
-- =========================================================================
DROP TABLE IF EXISTS order_line_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;

-- =========================================================================
-- 2. 주문 관련 테이블 생성 (DDL)
-- =========================================================================

-- 'orders' 테이블 생성 (주문 정보)
CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,                -- 주문 고유 식별자 (자동 증가)
    user_id VARCHAR(255) NOT NULL,             -- 주문한 사용자 ID (Keycloak UUID)
    store_id BIGINT NOT NULL,                  -- 주문한 가게 ID (stores 테이블의 id 참조 가정)
    order_date TIMESTAMP WITHOUT TIME ZONE NOT NULL, -- 주문 일시
    total_amount DECIMAL(19, 2) NOT NULL,      -- 주문 총액 (소수점 2자리까지)
    order_status VARCHAR(50) NOT NULL,         -- 주문 상태 (예: PENDING, PAYMENT_COMPLETED, CANCELED)
    payment_id BIGINT,                         -- 결제 ID (선택 사항)
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(), -- 레코드 생성 시간
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()  -- 레코드 수정 시간 (R2DBC Auditing 미적용 시 수동 업데이트 필요)
    -- stores(id)에 대한 외래 키 제약 조건은 stores 테이블이 확실히 존재하고,
    -- 이 스크립트가 stores 스크립트 이후에 실행된다는 보장이 있을 때 추가 가능합니다.
    -- 여기서는 단순화를 위해 주석 처리합니다. 필요시 추가:
    -- , CONSTRAINT fk_order_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

-- 'order_line_items' 테이블 생성 (주문 내 개별 항목 정보)
CREATE TABLE order_line_items (
    order_item_id SERIAL PRIMARY KEY,          -- 주문 항목 고유 식별자 (자동 증가)
    order_id BIGINT NOT NULL,                  -- 이 항목이 속한 주문의 ID (orders 테이블의 order_id 참조)
    menu_id VARCHAR(255) NOT NULL,             -- 주문한 메뉴의 ID (외부 시스템 ID 또는 단순 문자열)
    menu_name VARCHAR(255) NOT NULL,           -- 주문한 메뉴의 이름
    quantity INT NOT NULL,                     -- 주문 수량
    unit_price DECIMAL(19, 2) NOT NULL,        -- 메뉴 단가
    total_price DECIMAL(19, 2) NOT NULL,       -- 항목별 총액 (수량 * 단가)
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(), -- 레코드 생성 시간
    CONSTRAINT fk_order_line_item_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);