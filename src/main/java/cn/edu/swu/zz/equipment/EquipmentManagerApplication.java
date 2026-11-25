package cn.edu.swu.zz.equipment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class EquipmentManagerApplication {
    public static void main(String[] args) { SpringApplication.run(EquipmentManagerApplication.class, args); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
