package net.uku3lig.mcibot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class Util {
    public static ModelAndView error(String msg) {
        log.error(msg);
        return new ModelAndView("redirect:/error", Map.of("err_msg", msg));
    }

    public static UUID convertUUID(String uuid) {
        return UUID.fromString(uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }

    private Util() {}
}
