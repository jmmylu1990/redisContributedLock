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

    public boolean enrollCourseV1(Long studentId, Long courseId, String seat) {
        String lockKey = "course:lock:" + courseId; // 鎖的鍵名
        RLock lock = redissonClient.getLock(lockKey);
        String courseKey = "course:data:" + courseId; // 座位數據的鍵名

        try {
            if (lock.tryLock()) {
                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                // 檢查座位是否可用
                String seatStatus = hashOps.get(courseKey, seat);
                System.out.println("座位狀態：" + seatStatus);

                if ("available".equals(seatStatus)) {
                    // 標記座位為此學生選擇
                    hashOps.put(courseKey, seat, studentId.toString());

                    // 創建並保存選課記錄
                    EnrollmentRecord record = new EnrollmentRecord();
                    record.setStudentId(studentId);
                    record.setCourseId(courseId);
                    record.setSeat(seat);
                    enrollmentRecordRepository.save(record);

                    // 更新課程的可用座位數
                    Course course = courseRepository.findById(courseId).orElseThrow();
                    int availableSeats = course.getAvailableSeats();
                    if (availableSeats > 0) {
                        course.setAvailableSeats(availableSeats - 1);
                        courseRepository.save(course);
                    }

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
