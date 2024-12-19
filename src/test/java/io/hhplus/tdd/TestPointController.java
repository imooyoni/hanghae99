package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPointController {
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    @DisplayName("포인트 사용 실패 테스트 - 잔고 부족")
    void usePointFail_InsufficientBalance() {
        // Given
        long id = 1L;
        long useAmount = 2000L;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                pointService.chargeOrUsePoint(id, useAmount, 2)); // 2 = USE

        assertThat(exception.getMessage()).isEqualTo("Point usage failed (999: Insufficient remaining points)");
        assertThat(userPointTable.selectById(id).point()).isEqualTo(0L);
        assertThat(pointHistoryTable.selectAllByUserId(id)).isEmpty();
    }

    @Test
    @DisplayName("유효하지 않은 transactionCode 테스트")
    void invalidTransactionCode() {
        // Given
        long id = 1L;
        long amount = 100L;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                pointService.chargeOrUsePoint(id, amount, 99)); // Invalid code: 99

        assertThat(exception.getMessage()).isEqualTo("transactionCode is invalid");
    }

    @Test
    @DisplayName("포인트 충전 성공 테스트")
    void chargePointSuccess() {
        // Given
        long id = 1L;
        long amount = 500L;

        // When
        UserPoint result = pointService.chargeOrUsePoint(id, amount, 1); // 1 = CHARGE

        // Then
        assertThat(result.point()).isEqualTo(500L);
        assertThat(userPointTable.selectById(id).point()).isEqualTo(500L);
        assertThat(pointHistoryTable.selectAllByUserId(id)).hasSize(1);
    }

    @Test
    @DisplayName("포인트 사용 성공 테스트")
    void usePointSuccess() {
        // Given
        long id = 1L;
        long chargeAmount = 1000L;
        long useAmount = 500L;

        pointService.chargeOrUsePoint(id, chargeAmount, 1); // 테스트를 위한 포인트 충전

        // When
        UserPoint result = pointService.chargeOrUsePoint(id, useAmount, 2); // 2 = USE

        // Then
        assertThat(result.point()).isEqualTo(500L);
        assertThat(userPointTable.selectById(id).point()).isEqualTo(500L);
        assertThat(pointHistoryTable.selectAllByUserId(id)).hasSize(2); // 충전, 사용 내역 조회
    }

    @Test
    @DisplayName("동시 포인트 충전 테스트 - 서비스 레이어 동기화")
    void concurrentChargeTest() throws InterruptedException {
        long userId = 1L;
        int threadCount = 10;
        long chargeAmount = 100L;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    pointService.chargeOrUsePoint(userId, chargeAmount, 1); // 충전
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 쓰레드의 작업이 끝날 때까지 대기
        UserPoint result = userPointTable.selectById(userId);

        assertThat(result.point()).isEqualTo(threadCount * chargeAmount); // 10 * 100 = 1000
    }

}