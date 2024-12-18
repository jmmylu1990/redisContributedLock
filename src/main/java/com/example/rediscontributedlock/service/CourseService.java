package com.example.rediscontributedlock.service;

import com.example.rediscontributedlock.entity.Course;
import com.example.rediscontributedlock.entity.EnrollmentRecord;
import com.example.rediscontributedlock.repository.CourseRepository;
import com.example.rediscontributedlock.repository.EnrollmentRecordRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class CourseService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EnrollmentRecordRepository enrollmentRecordRepository;
    @Autowired
    private CourseRepository courseRepository;

    /**
     * Redisson 鎖的核心貢獻是通過分布式鎖機制，將以下操作串聯在一個原子性事務中：
     * 檢查座位狀態
     * 減少課程座位數量
     * 更新選課記錄
     * 更新 Redis 座位狀態
     * //這樣即使多個請求同時嘗試操作同一個課程的座位，也可以避免資料的不一致和資源爭奪問題。
     **/
    public boolean enrollCourseV1(Long studentId, Long courseId, String seat) {
        //利用 Redisson 提供的分布式鎖功能，為每門課程的座位設置一個獨立的鎖（course:lock:<courseId>:<seat>）。
        String lockKey = "course:lock:" + courseId + "seat"; // 鎖的鍵名
        RLock lock = redissonClient.getLock(lockKey);
        String courseKey = "course:data:" + courseId; // 座位數據的鍵名

        try {
            //使用 tryLock() 方法嘗試獲取鎖，如果獲取成功，則進行選課邏輯。
            //如果無法獲取鎖（例如，其他學生正在處理該課程），則返回選課失敗。
            if (lock.tryLock()) {
                // 檢查座位狀態
                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
                // 檢查座位是否可用
                String seatStatus = hashOps.get(courseKey, seat);
                System.out.println("座位狀態：" + seatStatus);
                System.out.println("studentId: "+ studentId);
               //檢查座位是否已被其他並發請求修改
                if ("available".equals(seatStatus)) {
                    //檢查學生是否已經選過
                    boolean alreadyEnrolled = enrollmentRecordRepository.existsByStudentIdAndCourseId(studentId, courseId);
                    if (alreadyEnrolled) {
                        System.out.println("學生 " + studentId + " 已經選擇過課程 " + courseId + "，無法重複選擇座位。");
                        return false; // 防止同一學生對同一課程多次選擇座位
                    }
                    // 更新課程的可用座位數
                    Course course = courseRepository.findById(courseId).orElseThrow();
                    int availableSeats = course.getAvailableSeats();
                    if (availableSeats > 0) {
                        course.setAvailableSeats(availableSeats - 1);
                        courseRepository.save(course);
                    }
                    // 創建並保存選課記錄
                    EnrollmentRecord record = new EnrollmentRecord();
                    record.setStudentId(studentId);
                    record.setCourseId(courseId);
                    record.setSeat(seat);
                    enrollmentRecordRepository.save(record);

                    // 標記座位為此學生選擇
                    hashOps.put(courseKey, seat, studentId.toString());
                    return true; // 選課成功
                } else {
                    System.out.println("座位 " + seat + " 已被佔用。");
                    return false; // 座位已被佔用
                }
            } else {
                System.out.println("無法獲取鎖，選課失敗。");
                return false; // 無法獲取鎖
            }
        } catch (Exception e) {
            System.err.println("選課過程中出現錯誤: " + e.getMessage());
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock(); // 確保在完成後釋放鎖
            }
        }
    }




}
