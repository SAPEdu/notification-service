package com.example.notificationservice.telegram;

import com.example.notificationservice.entity.NotificationPreference;
import com.example.notificationservice.repository.NotificationPreferenceRepository;
import com.example.notificationservice.service.TelegramLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

/**
 * Telegram Bot handler for processing incoming messages and commands.
 * Supports user account linking via secure tokens.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true", matchIfMissing = false)
public class TelegramBotHandler extends TelegramLongPollingBot {

    private final TelegramLinkService linkService;
    private final NotificationPreferenceRepository preferenceRepository;
    private final String botUsername;

    public TelegramBotHandler(
            TelegramLinkService linkService,
            NotificationPreferenceRepository preferenceRepository,
            @Value("${app.telegram.bot-token}") String botToken,
            @Value("${app.telegram.bot-username}") String botUsername) {
        super(botToken);
        this.linkService = linkService;
        this.preferenceRepository = preferenceRepository;
        this.botUsername = botUsername;
        log.info("TelegramBotHandler initialized with username: {}", botUsername);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String text = update.getMessage().getText().trim();
        String chatId = update.getMessage().getChatId().toString();
        String username = update.getMessage().getFrom().getUserName();

        log.debug("Received message from chatId {}: {}", chatId, text);

        if (text.startsWith("/start ")) {
            // /start <token> - Link account with token
            String token = text.substring(7).trim();
            handleStartWithToken(chatId, token, username);
        } else if (text.equals("/start")) {
            // Plain /start without token
            handleStart(chatId);
        } else if (text.equals("/status")) {
            handleStatus(chatId);
        } else if (text.equals("/stop") || text.equals("/unlink")) {
            handleUnlink(chatId);
        } else if (text.equals("/help")) {
            handleHelp(chatId);
        }
    }

    private void handleStartWithToken(String chatId, String token, String username) {
        boolean success = linkService.verifyAndLink(token, chatId);

        if (success) {
            String message = """
                    ✅ *Liên kết thành công!*

                    Tài khoản Telegram của bạn đã được liên kết với hệ thống.

                    Bạn sẽ nhận được thông báo về:
                    • 📚 Assessment mới được giao
                    • ⏰ Nhắc nhở trước deadline
                    • 📊 Kết quả chấm điểm

                    Gõ /help để xem các lệnh khả dụng.
                    """;
            sendTextMessage(chatId, message);
            log.info("User linked Telegram: chatId={}, username={}", chatId, username);
        } else {
            String message = """
                    ❌ *Link đã hết hạn hoặc không hợp lệ*

                    Vui lòng tạo link mới từ ứng dụng web:
                    1. Đăng nhập vào hệ thống
                    2. Vào Cài đặt > Thông báo > Liên kết Telegram
                    3. Quét mã QR hoặc nhấp vào link mới
                    """;
            sendTextMessage(chatId, message);
        }
    }

    private void handleStart(String chatId) {
        // Check if already linked
        Optional<NotificationPreference> linked = preferenceRepository.findByTelegramChatId(chatId);

        if (linked.isPresent() && Boolean.TRUE.equals(linked.get().getTelegramEnabled())) {
            String message = """
                    👋 *Chào mừng bạn quay lại!*

                    Tài khoản Telegram này đã được liên kết.

                    Gõ /status để xem trạng thái
                    Gõ /help để xem các lệnh khả dụng
                    """;
            sendTextMessage(chatId, message);
        } else {
            String message = """
                    👋 *Xin chào!*

                    Để nhận thông báo qua Telegram, vui lòng liên kết tài khoản:

                    1. Đăng nhập vào ứng dụng web
                    2. Vào Cài đặt > Thông báo > Liên kết Telegram
                    3. Quét mã QR hoặc nhấp vào link

                    ⚠️ Vì lý do bảo mật, bạn không thể liên kết trực tiếp từ đây.
                    """;
            sendTextMessage(chatId, message);
        }
    }

    private void handleStatus(String chatId) {
        Optional<NotificationPreference> prefOpt = preferenceRepository.findByTelegramChatId(chatId);

        if (prefOpt.isPresent() && Boolean.TRUE.equals(prefOpt.get().getTelegramEnabled())) {
            NotificationPreference pref = prefOpt.get();
            String linkedAt = pref.getTelegramLinkedAt() != null
                    ? pref.getTelegramLinkedAt().toString()
                    : "N/A";

            String message = String.format("""
                    📊 *Trạng thái Telegram*

                    ✅ Trạng thái: Đã liên kết
                    📅 Liên kết lúc: %s
                    🔔 Thông báo: %s
                    """,
                    linkedAt,
                    Boolean.TRUE.equals(pref.getTelegramEnabled()) ? "Bật" : "Tắt");
            sendTextMessage(chatId, message);
        } else {
            sendTextMessage(chatId, "❌ Tài khoản Telegram này chưa được liên kết với hệ thống.");
        }
    }

    private void handleUnlink(String chatId) {
        Optional<NotificationPreference> prefOpt = preferenceRepository.findByTelegramChatId(chatId);

        if (prefOpt.isPresent()) {
            NotificationPreference pref = prefOpt.get();
            pref.setTelegramEnabled(false);
            pref.setTelegramChatId(null);
            pref.setTelegramLinkedAt(null);
            preferenceRepository.save(pref);

            sendTextMessage(chatId, """
                    ✅ *Đã hủy liên kết*

                    Bạn sẽ không còn nhận thông báo qua Telegram nữa.

                    Để liên kết lại, vui lòng sử dụng link từ ứng dụng web.
                    """);
            log.info("User unlinked Telegram: chatId={}", chatId);
        } else {
            sendTextMessage(chatId, "❌ Tài khoản này chưa được liên kết.");
        }
    }

    private void handleHelp(String chatId) {
        String message = """
                📖 *Các lệnh khả dụng*

                /status - Xem trạng thái liên kết
                /stop - Hủy liên kết Telegram
                /help - Hiển thị trợ giúp này

                💡 *Lưu ý:* Để liên kết hoặc thay đổi cài đặt thông báo, vui lòng sử dụng ứng dụng web.
                """;
        sendTextMessage(chatId, message);
    }

    private void sendTextMessage(String chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            message.setParseMode("Markdown");
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to {}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Send message from external service (e.g., NotificationService).
     */
    public void sendNotification(String chatId, String text) {
        sendTextMessage(chatId, text);
    }
}
