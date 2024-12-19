package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // 유저 단위 Lock을 관리하기 위한 Map
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

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
     * charge or use point
     * 잔고가 부족할 경우 포인트 사용 실패
     * @param id        사용자 ID
     * @param amount    포인트 금액
     * @param transactionCode 1=charge, 2=use
     * @return UserPoint
     */
    public UserPoint chargeOrUsePoint(long id, long amount, long transactionCode) {
        Lock lock = userLocks.computeIfAbsent(id, key -> new ReentrantLock());

        lock.lock();

        try {
            // 현재 포인트 조회
            long currentPoint = userPointTable.selectById(id).point();

            if (transactionCode == 1) { // 포인트 충전
                long updatedPoint = currentPoint + amount;

                // 업데이트 및 히스토리 기록
                UserPoint updatedUserPoint = userPointTable.insertOrUpdate(id, updatedPoint);
                pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());
                return updatedUserPoint;

            } else if (transactionCode == 2) { // 포인트 사용
                if (currentPoint < amount) {
                    throw new RuntimeException("Point usage failed (999: Insufficient remaining points)");
                }
                long updatedPoint = currentPoint - amount;

                // 업데이트 및 히스토리 기록
                UserPoint updatedUserPoint = userPointTable.insertOrUpdate(id, updatedPoint);
                pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());
                return updatedUserPoint;

            } else {
                throw new RuntimeException("transactionCode is invalid");
            }
        } finally {
            lock.unlock(); // Lock 해제
        }
    }
}
