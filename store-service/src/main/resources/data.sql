---- data.sql
--INSERT INTO stores (owner_id, name, description, address, latitude, longitude, phone, opening_hours, status, category, main_image_url, gallery_image_urls_json, created_at, updated_at) VALUES
--('user-owner-1-uuid', '싱싱 야채가게', '매일 아침 공수하는 신선한 채소와 과일 전문점입니다. 유기농 상품 다수 보유!', '서울시 강남구 테헤란로 123', 37.50170000, 127.03960000, '02-1111-2222', '매일 08:00 - 20:00', 'OPEN', 'FRUITS_VEGETABLES', '/store-images/store1_main.jpg', '["/store-images/store1_gallery1.jpg", "/store-images/store1_gallery2.jpg"]', NOW(), NOW()),
--('user-owner-2-uuid', '우리동네 정육점', '1등급 한우와 한돈만을 취급하는 프리미엄 정육점. 원하는 두께로 손질해 드립니다.', '서울시 서초구 강남대로 456', 37.49790000, 127.02760000, '02-3333-4444', '평일 09:00 - 21:00, 주말 10:00 - 20:00', 'OPEN', 'MEAT_BUTCHER', '/store-images/store2_main.jpg', '["/store-images/store2_gallery1.jpg"]', NOW(), NOW()),
--('user-owner-1-uuid', '행복 베이커리', '매일 직접 구운 천연 발효빵과 달콤한 디저트가 가득한 동네 빵집입니다.', '서울시 마포구 양화로 789', 37.55590000, 126.92380000, '02-5555-6666', '매일 07:00 - 22:00, 월요일 휴무', 'OPEN', 'BREAD_BAKERY', '/store-images/store3_main.jpg', null, NOW(), NOW());
--
--
---- 2. 메뉴 데이터 삽입 (menus)
---- store_id는 위에서 삽입한 가게의 ID를 참조합니다.
--INSERT INTO menus (store_id, name, description, price, image_url, is_available, created_at, updated_at) VALUES
---- 싱싱 야채가게 (store_id: 1) 메뉴
--(1, '유기농 상추 (1봉)', '깨끗하게 재배된 아삭한 유기농 상추입니다.', 3500.00, '/images/menu_lettuce.jpg', TRUE, NOW(), NOW()),
--(1, 'GAP 인증 딸기 (1팩)', 'GAP 인증을 받은 달콤한 설향 딸기입니다. (500g)', 7900.00, '/images/menu_strawberry.jpg', TRUE, NOW(), NOW()),
--(1, '제주산 감귤 (1kg)', '비타민C 가득한 새콤달콤 제주 감귤.', 6000.00, '/images/menu_tangerine.jpg', FALSE, NOW(), NOW()), -- 판매 중지 예시
--
---- 우리동네 정육점 (store_id: 2) 메뉴
--(2, '한우 등심 (100g)', '마블링이 뛰어난 1++ 등급 한우 등심입니다.', 15000.00, '/images/menu_beef_sirloin.jpg', TRUE, NOW(), NOW()),
--(2, '국내산 삼겹살 (500g)', '잡내 없이 고소한 국내산 냉장 삼겹살입니다.', 18000.00, '/images/menu_pork_belly.jpg', TRUE, NOW(), NOW()),
--
---- 행복 베이커리 (store_id: 3) 메뉴
--(3, '천연효모 건강빵', '속이 편안한 천연 발효종으로 만든 건강빵입니다.', 5500.00, '/images/menu_sourdough.jpg', TRUE, NOW(), NOW()),
--(3, '딸기 생크림 케이크 (조각)', '신선한 딸기와 부드러운 우유 생크림의 조화.', 6500.00, '/images/menu_cake_strawberry.jpg', TRUE, NOW(), NOW());
--
--
---- 3. 리뷰 데이터 삽입 (reviews)
---- store_id는 가게 ID, user_id는 리뷰 작성자 ID를 나타냅니다.
--INSERT INTO reviews (store_id, user_id, rating, comment, image_url, created_at, updated_at) VALUES
---- 싱싱 야채가게 (store_id: 1) 리뷰
--(1, 'user-normal-1-uuid', 5, '채소가 정말 신선하고 좋아요! 배송도 빠릅니다.', '/review-images/review1_user1.jpg', NOW() - INTERVAL '2 day', NOW() - INTERVAL '2 day'),
--(1, 'user-normal-2-uuid', 4, '딸기가 조금 물렀지만 전반적으로 만족합니다.', null, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
--
---- 우리동네 정육점 (store_id: 2) 리뷰
--(2, 'user-normal-1-uuid', 5, '사장님이 정말 친절하시고 고기 품질이 최상입니다! 항상 여기서 구매해요.', null, NOW() - INTERVAL '5 day', NOW() - INTERVAL '5 day'),
--(2, 'user-owner-1-uuid', 4, '삼겹살 맛있네요. 다음엔 등심도 먹어봐야겠어요.', '/review-images/review2_user_owner1.jpg', NOW() - INTERVAL '3 day', NOW() - INTERVAL '3 day'),
--
---- 행복 베이커리 (store_id: 3) 리뷰
--(3, 'user-normal-2-uuid', 5, '여기 케이크는 정말 인생 케이크입니다 ㅠㅠ 너무 맛있어요!', '/review-images/review3_user2.jpg', NOW() - INTERVAL '10 minute', NOW() - INTERVAL '10 minute');
--
