package com.example.holdings.holding;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.holdings.common.ApiException;

@Controller
@RequestMapping("/holdings")
public class HoldingPageController {
    private final HoldingService service;

    public HoldingPageController(HoldingService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("holdings", service.listWithQuotes());
        return "holdings";
    }

    @PostMapping
    public String create(@RequestParam String code, @RequestParam(required = false) Integer lots,
                         @RequestParam(required = false) BigDecimal buyPrice, RedirectAttributes ra) {
        return run(ra, () -> service.create(code, lots, buyPrice), "已新增 " + code.trim());
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable long id, @RequestParam(required = false) Integer lots,
                         @RequestParam(required = false) BigDecimal buyPrice, RedirectAttributes ra) {
        return run(ra, () -> service.update(id, lots, buyPrice), "已修改");
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes ra) {
        return run(ra, () -> service.delete(id), "已刪除");
    }

    private String run(RedirectAttributes ra, Runnable action, String ok) {
        try {
            action.run();
            ra.addFlashAttribute("message", ok);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/holdings";
    }
}
