# **Java 17과 Spring Boot에서의 동시성 제어 방식**

## **1. 동시성 제어 방식 정리**

### **1.1 `Synchronized` 키워드**
- **설명**: 메서드나 코드 블록에 동기화를 제공하는 Java의 기본 키워드입니다. JVM에서 제공하는 **모니터 락**을 활용합니다.  
- **특징**:  
  - **간단함**: 명시적으로 키워드를 추가하는 것으로 동기화 가능.  
  - **자동 해제**: 락은 예외가 발생하더라도 자동으로 해제됩니다.  
  - **성능**: Java 17에서는 JVM의 **경량화 락(Lightweight Locking)** 및 **바이어스 락(Biased Locking)** 최적화가 있어 성능이 개선되었습니다.  

- **사용 예시**:
   ```java
   public synchronized void syncMethod() {
       // 동기화된 메서드
   }
   
   public void syncBlock() {
       synchronized (this) {
           // 동기화 블록
       }
   }
   ```
- **단점**:  
  - 세밀한 제어가 어렵습니다. 예를 들어, 공평한 락(Fair Lock)을 지원하지 않습니다.  
  - 성능 저하 가능성이 있습니다 (락 경합 시).  

---

### **1.2 `ReentrantLock` (java.util.concurrent.locks)**
- **설명**: `java.util.concurrent` 패키지에서 제공하는 Lock 인터페이스의 구현체로 더 세밀한 동기화 제어를 제공합니다.  
- **특징**:
  - **재진입 가능**: 동일 스레드가 이미 획득한 락을 다시 획득할 수 있습니다.  
  - **공정성**: 락 획득 순서를 설정할 수 있습니다 (Fair vs Unfair).  
  - **tryLock()**: 특정 시간 동안 락 획득을 시도하거나 실패할 수 있습니다.  
  - **조건 변수 지원**: `newCondition()`을 통해 여러 조건을 활용한 대기/알림을 구현합니다.  

- **사용 예시**:
   ```java
   ReentrantLock lock = new ReentrantLock(true); // 공정한 락
   try {
       lock.lock();
       // 공유 자원 접근
   } finally {
       lock.unlock();
   }
   ```
- **단점**:  
  - 코드가 복잡해집니다.  
  - 명시적으로 `unlock()`을 호출해야 하므로 실수로 락을 해제하지 않을 수 있습니다.  

---

### **1.3 `Semaphore`**
- **설명**: 제한된 수의 스레드만 동시에 공유 자원에 접근하도록 제어하는 도구입니다.  
- **특징**:  
  - **허용 수 설정**: `new Semaphore(int permits)`를 통해 접근 가능한 스레드 수를 설정합니다.  
  - **다중 자원 동시 제어**: 예를 들어, 데이터베이스 커넥션 풀과 같은 리소스를 제어할 때 유용합니다.  

- **사용 예시**:
   ```java
   Semaphore semaphore = new Semaphore(3); // 최대 3개의 스레드 접근
   try {
       semaphore.acquire();
       // 공유 자원 접근
   } finally {
       semaphore.release();
   }
   ```
- **단점**:  
  - 너무 많은 스레드에서 경쟁하면 대기 시간이 길어질 수 있습니다.  

---

### **1.4 `Atomic` 클래스 (java.util.concurrent.atomic)**
- **설명**: Lock 없이도 **CAS(Compare-And-Swap)**를 사용해 원자적 연산을 제공하는 클래스입니다.  
- **특징**:  
  - **성능**: 락이 필요 없기 때문에 속도가 빠릅니다.  
  - **사용 용도**: 주로 카운터, 플래그와 같은 단순 연산에 사용됩니다.  
  - **클래스 예시**: `AtomicInteger`, `AtomicLong`, `AtomicReference` 등  

- **사용 예시**:
   ```java
   AtomicInteger counter = new AtomicInteger(0);
   counter.incrementAndGet(); // 원자적 증가
   ```
- **단점**:  
  - 복잡한 동기화 로직은 구현하기 어렵습니다.  
  - CAS가 실패하면 재시도해야 하므로 스핀락 형태가 됩니다.  

---

### **1.5 `VarHandle` (java.lang.invoke)**
- **설명**: Java 9부터 도입된 VarHandle은 더 유연하고 성능이 좋은 메모리 접근 방식을 제공합니다.  
- **특징**:  
  - 기존 `Atomic` 클래스와 유사한 원자적 연산을 제공합니다.  
  - `VarHandle`을 사용하면 필드에 대해 원자적 연산, 가시성 제어 등을 할 수 있습니다.  
  - JVM 내부적으로 최적화가 적용됩니다.  

- **사용 예시**:
   ```java
   import java.lang.invoke.MethodHandles;
   import java.lang.invoke.VarHandle;

   public class VarHandleExample {
       private volatile int count = 0;
       private static final VarHandle COUNT_HANDLE;

       static {
           try {
               COUNT_HANDLE = MethodHandles.lookup()
                   .findVarHandle(VarHandleExample.class, "count", int.class);
           } catch (ReflectiveOperationException e) {
               throw new Error(e);
           }
       }

       public void increment() {
           COUNT_HANDLE.getAndAdd(this, 1);
       }
   }
   ```
- **단점**:  
  - 코드 가독성이 떨어집니다.  
  - 비교적 새로운 기능이라 학습이 필요합니다.  

---

## **2. Spring Boot와의 통합 시 고려 사항**
Spring Boot 애플리케이션에서 동시성 제어를 적용할 때는 다음과 같은 점을 고려해야 합니다.  

1. **단순 동기화**:  
   - `synchronized` 키워드 또는 `ReentrantLock`을 사용하면 충분한 경우가 많습니다.  
2. **데이터 접근 레이어**:  
   - **Spring Transaction**과 함께 사용할 경우 `@Transactional`이 적절한 동시성 제어를 제공할 수 있습니다.  
3. **경합이 심한 환경**:  
   - 고성능이 필요하다면 `Atomic` 클래스 또는 `VarHandle`을 사용해 락 없는 동기화를 고려합니다.  
4. **자원 제한**:  
   - 데이터베이스 커넥션 풀이나 제한된 리소스를 관리할 때는 `Semaphore`를 사용합니다.  

---

## **3. 추천 동시성 제어 방법**

**Java 17과 Spring Boot를 사용할 때 가장 효과적인 방법은 다음과 같습니다:**  

### **간단한 동기화**  
- `synchronized` 키워드 (JVM 최적화가 잘 되어 있음).  

### **세밀한 제어가 필요할 때**  
- **ReentrantLock**: 공정성 제어나 `tryLock`과 같은 세부 기능이 필요할 때.  

### **락 없는 동기화**  
- **Atomic** 클래스 또는 **VarHandle**: 성능이 중요하고 원자적 연산만 필요할 때.  

### **자원 관리**  
- **Semaphore**: 제한된 자원에 대한 접근 제어 (예: 최대 n개의 스레드만 접근).  

---

## **결론**
- **간단한 상황**: `synchronized` 키워드  
- **고성능 원자적 연산**: `Atomic` 클래스 또는 `VarHandle`  
- **세밀한 제어**: `ReentrantLock`  
- **제한된 리소스 제어**: `Semaphore`  

상황에 맞게 위의 도구들을 적절히 조합하면 효과적으로 동시성을 제어할 수 있습니다.

[참고글] : https://enumclass.tistory.com/169#toc-%EC%95%8C%EA%B3%A0%EC%9E%90%20%ED%95%98%EB%8A%94%20%EA%B2%83%20:%20Synchronized%C2%A0keyword,%20ReentrantLock,%20Semaphore,%20Atomic,%20varHandle
