package net.uku3lig.mcibot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@Slf4j
public class Util {
    public static ModelAndView error(String msg) {
        log.error(msg);
        return new ModelAndView("redirect:/error", Map.of("err_msg", msg));
    }

    private Util() {}
}
