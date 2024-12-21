package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // 유저 단위 Lock을 관리하기 위한 Map
    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * select userpoint by user id
     * @param id
     * @return UserPoint
     */
    public UserPoint selectUserPointById(long id){
        return userPointTable.selectById(id);
    }

    /**
     * select used point history by user id
     * @param id
     * @return List<PointHistory>
     */
    public List<PointHistory> selectPointHistoryById(long id){
        return pointHistoryTable.selectAllByUserId(id);
    }

    /**
     * 포인트를 충전하거나 사용합니다.
     * @param id 사용자 ID
     * @param amount 포인트 금액 (양수)
     * @param type 트랜잭션 타입 (CHARGE 또는 USE)
     * @return 업데이트된 UserPoint
     * @throws IllegalArgumentException amount가 0 이하인 경우
     * @throws RuntimeException 잔액 부족으로 실패시
     */
    public UserPoint chargeOrUsePoint(long id, long amount, TransactionType type) {
        validateAmount(amount);

        ReentrantLock lock = userLocks.computeIfAbsent(id, key -> new ReentrantLock());
        lock.lock();

        try {
            UserPoint currentUserPoint = userPointTable.selectById(id);
            long updatedPoint = calculateUpdatedPoint(currentUserPoint.point(), amount, type);

            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(id, updatedPoint);
            pointHistoryTable.insert(id, amount, type, System.currentTimeMillis());

            return updatedUserPoint;
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                userLocks.remove(id);
            }
        }
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
    }

    private long calculateUpdatedPoint(long currentPoint, long amount, TransactionType type) {
        return switch (type) {
            case CHARGE -> currentPoint + amount;
            case USE -> {
                if (currentPoint < amount) {
                    throw new RuntimeException("Point usage failed (999: Insufficient remaining points)");
                }
                yield currentPoint - amount;
            }
        };
    }
}
