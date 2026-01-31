-- ============================================================================
-- 푸시 알림 성능 테스트용 데이터 셋업
-- ============================================================================

-- 비밀번호: test1234 (BCrypt 인코딩)
-- 온라인 BCrypt 생성기 사용 또는 Spring에서 생성한 값 사용
SET @TEST_PASSWORD = '$2a$10$N9qo8uLOickgx2ZMRZoMye.IQzQZhkY0h0DXkxJ2n.QXHX6Qj5F7e';

-- ============================================================================
-- 1. 테스트용 부서 (없으면 생성)
-- ============================================================================
INSERT IGNORE INTO department (department_id, name, created_at, updated_at)
VALUES (1, '개발팀', NOW(), NOW());

INSERT IGNORE INTO department (department_id, name, created_at, updated_at)
VALUES (2, 'QA팀', NOW(), NOW());

-- ============================================================================
-- 2. 테스트용 직급 (없으면 생성)
-- ============================================================================
INSERT IGNORE INTO job_position (job_position_id, name, created_at, updated_at)
VALUES (1, '사원', NOW(), NOW());

INSERT IGNORE INTO job_position (job_position_id, name, created_at, updated_at)
VALUES (2, '대리', NOW(), NOW());

-- ============================================================================
-- 3. 테스트용 카테고리 (없으면 생성)
-- ============================================================================
INSERT IGNORE INTO category (category_id, name, created_at, updated_at)
VALUES (1, '일반', NOW(), NOW());

-- ============================================================================
-- 4. 테스트용 알림 설정 (이슈 생성 알림 활성화)
-- ============================================================================
INSERT INTO set_notification (id, issue_created, issue_status, meeting_created, created_at, updated_at)
VALUES (1, true, true, true, NOW(), NOW())
ON DUPLICATE KEY UPDATE issue_created = true, issue_status = true;

-- ============================================================================
-- 5. 테스트용 멤버 10명 생성
-- ============================================================================

-- 기존 테스트 멤버 삭제 (선택사항 - 주석 해제 시 실행)
-- DELETE FROM member WHERE login_id LIKE 'testuser%';

INSERT INTO member (member_id, login_id, password, name, department_id, job_position_id, phone, email, is_employed, role, created_at, updated_at)
VALUES
(1001, 'testuser01', @TEST_PASSWORD, '테스트유저01', 1, 1, '010-1111-0001', 'test01@test.com', true, 'USER', NOW(), NOW()),
(1002, 'testuser02', @TEST_PASSWORD, '테스트유저02', 1, 1, '010-1111-0002', 'test02@test.com', true, 'USER', NOW(), NOW()),
(1003, 'testuser03', @TEST_PASSWORD, '테스트유저03', 1, 1, '010-1111-0003', 'test03@test.com', true, 'USER', NOW(), NOW()),
(1004, 'testuser04', @TEST_PASSWORD, '테스트유저04', 1, 2, '010-1111-0004', 'test04@test.com', true, 'USER', NOW(), NOW()),
(1005, 'testuser05', @TEST_PASSWORD, '테스트유저05', 1, 2, '010-1111-0005', 'test05@test.com', true, 'USER', NOW(), NOW()),
(1006, 'testuser06', @TEST_PASSWORD, '테스트유저06', 2, 1, '010-1111-0006', 'test06@test.com', true, 'USER', NOW(), NOW()),
(1007, 'testuser07', @TEST_PASSWORD, '테스트유저07', 2, 1, '010-1111-0007', 'test07@test.com', true, 'USER', NOW(), NOW()),
(1008, 'testuser08', @TEST_PASSWORD, '테스트유저08', 2, 2, '010-1111-0008', 'test08@test.com', true, 'USER', NOW(), NOW()),
(1009, 'testuser09', @TEST_PASSWORD, '테스트유저09', 2, 2, '010-1111-0009', 'test09@test.com', true, 'USER', NOW(), NOW()),
(1010, 'testuser10', @TEST_PASSWORD, '테스트유저10', 2, 2, '010-1111-0010', 'test10@test.com', true, 'USER', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================================
-- 6. 테스트용 푸시 구독 정보 (Redis에 저장 - 별도 처리 필요)
-- ============================================================================
-- 푸시 구독은 Redis에 저장되므로 아래 형식으로 Redis CLI에서 등록하거나
-- 프론트엔드에서 각 테스트 유저로 로그인 후 구독 등록 필요
--
-- Redis 명령어 예시:
-- SET push:subscription:1001 '{"endpoint":"https://fcm.googleapis.com/fcm/send/test1001","keys":{"p256dh":"test_p256dh_key","auth":"test_auth_key"}}'
-- SET push:subscription:1002 '{"endpoint":"https://fcm.googleapis.com/fcm/send/test1002","keys":{"p256dh":"test_p256dh_key","auth":"test_auth_key"}}'
-- ... 등

-- ============================================================================
-- 검증 쿼리
-- ============================================================================
SELECT 'Members' as type, COUNT(*) as count FROM member WHERE member_id BETWEEN 1001 AND 1010
UNION ALL
SELECT 'Departments' as type, COUNT(*) as count FROM department
UNION ALL
SELECT 'JobPositions' as type, COUNT(*) as count FROM job_position
UNION ALL
SELECT 'Categories' as type, COUNT(*) as count FROM category;
