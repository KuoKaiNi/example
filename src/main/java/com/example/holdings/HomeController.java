package com.example.holdings;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("now", ZonedDateTime.now(TAIPEI).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return "index";
    }
}
