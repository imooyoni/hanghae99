package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPointController {
    private static final long id = 1L;
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
        long useAmount = 2000L;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                pointService.chargeOrUsePoint(id, useAmount, TransactionType.USE)); // 2 = USE

        assertThat(exception.getMessage()).isEqualTo("Point usage failed (999: Insufficient remaining points)");

        assertAll(
                () -> assertThat(userPointTable.selectById(id).point()).isZero(),
                () -> assertThat(pointHistoryTable.selectAllByUserId(id)).isEmpty()
        );
    }

    @Test
    @DisplayName("포인트 충전 실패 - 음수 금액")
    void chargePoint_shouldThrowException_whenAmountIsNegative() {
        // Given
        long negativeAmount = -100L;

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> pointService.chargeOrUsePoint(id, negativeAmount, TransactionType.CHARGE));
    }

    @Test
    @DisplayName("포인트 충전 성공 테스트")
    void chargePointSuccess() {
        // Given
        long amount = 500L;

        // When
        UserPoint result = pointService.chargeOrUsePoint(id, amount, TransactionType.CHARGE); // 1 = CHARGE

        // Then
        assertAll(
                () -> assertThat(result.point()).isEqualTo(amount),
                () -> assertThat(userPointTable.selectById(id).point()).isEqualTo(amount),
                () -> {
                    List<PointHistory> history = pointHistoryTable.selectAllByUserId(id);
                    assertThat(history)
                            .hasSize(1)
                            .element(0)
                            .satisfies(record -> {
                                assertThat(record.amount()).isEqualTo(amount);
                                assertThat(record.type()).isEqualTo(TransactionType.CHARGE);
                            });
                }
        );
    }

    @Test
    @DisplayName("포인트 사용 성공 테스트")
    void usePointSuccess() {
        // Given
        long chargeAmount = 1000L;
        long useAmount = 500L;

        pointService.chargeOrUsePoint(id, chargeAmount, TransactionType.CHARGE); // 테스트를 위한 포인트 충전

        // When
        UserPoint result = pointService.chargeOrUsePoint(id, useAmount, TransactionType.USE); // 2 = USE

        // Then
        assertAll(
                () -> assertThat(result.point()).isEqualTo(chargeAmount - useAmount),
                () -> assertThat(userPointTable.selectById(id).point()).isEqualTo(chargeAmount - useAmount),
                () -> {
                    List<PointHistory> history = pointHistoryTable.selectAllByUserId(id);
                    assertThat(history)
                            .hasSize(2)
                            .satisfies(records -> {
                                assertThat(records.get(0).type()).isEqualTo(TransactionType.CHARGE);
                                assertThat(records.get(1).type()).isEqualTo(TransactionType.USE);
                            });
                }
        );
    }

    @Test
    @DisplayName("동시 포인트 충전 테스트 - 서비스 레이어 동기화")
    void concurrentChargeTest() throws InterruptedException {
        int threadCount = 10;
        long chargeAmount = 100L;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    pointService.chargeOrUsePoint(id, chargeAmount, TransactionType.CHARGE); // 충전
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 쓰레드의 작업이 끝날 때까지 대기
        UserPoint result = userPointTable.selectById(id);

        assertThat(result.point()).isEqualTo(threadCount * chargeAmount); // 10 * 100 = 1000
    }

}
