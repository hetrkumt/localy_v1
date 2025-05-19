-- 기존 테이블이 존재할 경우 삭제 (개발 환경에서 편리, 프로덕션에서는 신중)
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS menus;
DROP TABLE IF EXISTS stores;

-- 'stores' 테이블 생성
CREATE TABLE stores (
    id SERIAL PRIMARY KEY, -- PostgreSQL의 자동 증가 기본 키 타입 (SERIAL)
    owner_id VARCHAR(255) NOT NULL, -- 가게 주인 사용자 ID (문자열)
    name VARCHAR(255) NOT NULL, -- 가게 이름
    description TEXT, -- 가게 설명 (긴 문자열)
    address VARCHAR(255), -- 가게 주소
    latitude DECIMAL(10, 8), -- 위도 (소수점 포함 숫자)
    longitude DECIMAL(11, 8), -- 경도 (소수점 포함 숫자)
    phone VARCHAR(255), -- 가게 연락처
    opening_hours VARCHAR(255), -- 영업 시간 정보
    status VARCHAR(50), -- 가게 상태 (ENUM 대신 문자열로 저장)
    category VARCHAR(50), -- 가게 카테고리 (ENUM 대신 문자열로 저장)
    main_image_url VARCHAR(255), -- 가게 대표 이미지 URL (선택 사항)
    gallery_image_urls_json TEXT, -- 가게 갤러리 이미지 URL 목록 (JSON 문자열, 선택 사항)
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, -- 생성 시간 (타임존 정보 없음)
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL -- 수정 시간 (타임존 정보 없음)
);

-- 'menus' 테이블 생성
CREATE TABLE menus (
    id SERIAL PRIMARY KEY, -- 메뉴 고유 식별자 (SERIAL)
    store_id BIGINT NOT NULL, -- 이 메뉴가 속한 가게의 ID (외래 키)
    name VARCHAR(255) NOT NULL, -- 메뉴 이름
    description TEXT, -- 메뉴 설명
    price DECIMAL(10, 2) NOT NULL, -- 메뉴 가격 (소수점 포함 숫자)
    image_url VARCHAR(255), -- 메뉴 이미지 URL (선택 사항)
    is_available BOOLEAN NOT NULL DEFAULT TRUE, -- 메뉴 판매 가능 여부 (기본값 TRUE)
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, -- 생성 시간
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, -- 수정 시간

    -- 외래 키 제약 조건: menus.store_id는 stores.id를 참조
    -- ON DELETE CASCADE: 가게 삭제 시 해당 가게의 메뉴들도 함께 삭제
    CONSTRAINT fk_store
        FOREIGN KEY (store_id)
        REFERENCES stores (id)
        ON DELETE CASCADE
);

-- 'reviews' 테이블 생성
CREATE TABLE reviews (
    id SERIAL PRIMARY KEY, -- 리뷰 고유 식별자 (SERIAL)
    store_id BIGINT NOT NULL, -- 이 리뷰가 작성된 가게의 ID (외래 키)
    user_id VARCHAR(255) NOT NULL, -- 리뷰 작성자 사용자 ID (문자열)
    rating INT NOT NULL, -- 평점 (정수)
    comment TEXT, -- 리뷰 내용
    image_url VARCHAR(255), -- 리뷰 이미지 URL (선택 사항)
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, -- 생성 시간
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, -- 수정 시간

    -- 외래 키 제약 조건: reviews.store_id는 stores.id를 참조
    -- ON DELETE CASCADE: 가게 삭제 시 해당 가게의 리뷰들도 함께 삭제
    CONSTRAINT fk_store_review
        FOREIGN KEY (store_id)
        REFERENCES stores (id)
        ON DELETE CASCADE
);

-- R2DBC Auditing을 위한 트리거 또는 함수 설정 (선택 사항, DB 레벨에서 자동 시간 기록 시)
-- Spring Data R2DBC의 @CreatedDate, @LastModifiedDate를 사용하면 애플리케이션 레벨에서 처리됩니다.
-- DB 레벨에서 처리하려면 별도 설정 필요 (예: PostgreSQL 트리거)
