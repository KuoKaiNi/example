package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.holdings.common.ApiException;

@Controller
@RequestMapping("/holdings")
public class HoldingPageController {
    private final HoldingService service;
    private final Clock clock;

    public HoldingPageController(HoldingService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean closed, Model model) {
        var views = service.list(true);
        model.addAttribute("holdings", views.stream().filter(v -> closed || v.open()).toList());
        model.addAttribute("closedCount", views.stream().filter(v -> !v.open()).count());
        model.addAttribute("showClosed", closed);
        model.addAttribute("summary", service.summary(views));
        model.addAttribute("today", LocalDate.now(clock));
        return "holdings";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model, RedirectAttributes ra) {
        try {
            model.addAttribute("v", service.get(id));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/holdings";
        }
        model.addAttribute("today", LocalDate.now(clock));
        return "holding";
    }

    @PostMapping("/trades")
    public String addTrade(@RequestParam String code, @RequestParam Trade.Side side,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
                           @RequestParam(required = false) Integer shares,
                           @RequestParam(required = false) BigDecimal price,
                           @RequestParam(required = false) Long back, RedirectAttributes ra) {
        String verb = side == Trade.Side.BUY ? "買進" : "賣出";
        try {
            Trade t = service.addTrade(code, side, tradeDate, shares, price);
            ra.addFlashAttribute("message", "已記下" + verb + " " + code.trim() + " " + shares + " 股");
            return "redirect:/holdings/" + t.getHoldingId();
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return back == null ? "redirect:/holdings" : "redirect:/holdings/" + back;
        }
    }

    @PostMapping("/trades/{tradeId}/update")
    public String updateTrade(@PathVariable long tradeId, @RequestParam long back,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
                              @RequestParam(required = false) Integer shares,
                              @RequestParam(required = false) BigDecimal price, RedirectAttributes ra) {
        try {
            service.updateTrade(tradeId, tradeDate, shares, price);
            ra.addFlashAttribute("message", "已修改這筆交易");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/holdings/" + back;
    }

    @PostMapping("/trades/{tradeId}/delete")
    public String deleteTrade(@PathVariable long tradeId, @RequestParam long back, RedirectAttributes ra) {
        try {
            if (service.deleteTrade(tradeId)) {
                ra.addFlashAttribute("message", "已刪除最後一筆交易，這檔也一併移除");
                return "redirect:/holdings";
            }
            ra.addFlashAttribute("message", "已刪除這筆交易");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/holdings/" + back;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes ra) {
        try {
            service.deleteHolding(id);
            ra.addFlashAttribute("message", "已刪除整檔與所有買賣紀錄");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/holdings";
    }
}
