package com.example.Spring.Boot.Bot.service;

import com.example.Spring.Boot.Bot.config.BotConfig;
import com.example.Spring.Boot.Bot.model.User;
import com.example.Spring.Boot.Bot.model.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MyTelegramBot extends TelegramLongPollingBot {

    private static final String BUTTON_YES = "YES_BUTTON";
    private static final String BUTTON_NO = "NO_BUTTON";
    @Autowired
    private UserRepository userRepository;

    @Autowired
    final BotConfig config;

    public MyTelegramBot(BotConfig config) {
        super(config.getToken());
        this.config = config;
        generateAndSetBotCommands();
    }

    private void generateAndSetBotCommands() {
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Start the bot"));
        listOfCommands.add(new BotCommand("/mydata", "get you data stored"));
        listOfCommands.add(new BotCommand("/deletedata", "delete your data stored"));
        listOfCommands.add(new BotCommand("/help", "info on how to use the bot"));
        listOfCommands.add(new BotCommand("/settings", "set your preferences"));
        listOfCommands.add(new BotCommand("/register", "register your case"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText(), firstName = update.getMessage().getChat().getFirstName();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/send") && config.getOwnerId() == chatId) {
                var text = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (User user : users) {
                    sendMessage(user.getChatId(), text);
                }
            } else {

                switch (messageText) {
                    case "/start":
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, firstName);
                        break;
                    case "/mydata":
                        myDataCommandReceived(chatId, update.getMessage().getChat());
                        break;
                    case "/deletedata":
                        deleteDataCommandReceived(chatId, firstName);
                        break;
                    case "/help":
                        helpCommandReceived(chatId, firstName);
                        break;
                    case "/settings":
                        settingsCommandReceived(chatId, firstName);
                        break;
                    case "/register":
                        registerTriggered(chatId);
                        break;
                    default:
                        sendMessage(chatId, "Sorry, the command is not recognized.");
                }
            }

        } else if (update.hasCallbackQuery()) {
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();
            String text = "Nothing is pressed";

            if (callbackData.equals(BUTTON_YES)) {
                text = "You pressed Yes button";
            } else if (callbackData.equals(BUTTON_NO)) {
                text = "You pressed No button";
            }

            EditMessageText message = new EditMessageText();
            message.setChatId(String.valueOf(chatId));
            message.setMessageId(messageId);
            message.setText(text);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error occured on sending message: " + e.getMessage());
            }
        }


    }

    private void registerTriggered(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Do you really want to register");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton yesBtn = new InlineKeyboardButton();
        yesBtn.setText("Yes");
        yesBtn.setCallbackData(BUTTON_YES);

        InlineKeyboardButton noBtn = new InlineKeyboardButton();
        noBtn.setText("No");
        noBtn.setCallbackData(BUTTON_NO);

        row.add(yesBtn);
        row.add(noBtn);

        rowsInline.add(row);
        markup.setKeyboard(rowsInline);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occured on sending message: " + e.getMessage());
        }
    }

    private void registerUser(Message msg) {
        var id = msg.getChatId();
        if (userRepository.findById(id).isEmpty()) {
            var chat = msg.getChat();
            User user = new User();
            user.setChatId(id);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(Timestamp.valueOf(LocalDateTime.now()));

            userRepository.save(user);
            log.info("user saved: " + user);
        }
    }

    private void myDataCommandReceived(long chatId, Chat chat) {
        String answer = "Here is your info:\nFirst name: " + chat.getFirstName() + "\n"
                + "Last name: " + (chat.getLastName() == null ? ".!." : chat.getLastName()) + "\n"
                + "And you smart citation: " + (chat.getBio() == null ? ".!." : chat.getBio());
        log.info("Replied to user: " + answer);
        sendMessage(chatId, answer);
    }

    private void deleteDataCommandReceived(long chatId, String firstName) {
        String answer = firstName + ", your data has been removed!\nCheers!";
        log.info("Replied to user: " + answer);
        sendMessage(chatId, answer);
    }

    private void helpCommandReceived(long chatId, String firstName) {
        String answer = firstName + ", you don't need a help. Go and work your butt off!";
        log.info("Replied to user: " + answer);
        sendMessage(chatId, answer);
    }

    private void settingsCommandReceived(long chatId, String firstName) {
        String answer = firstName + ", no settings yet.\nThanks for your patience!";
        log.info("Replied to user: " + answer);
        sendMessage(chatId, answer);
    }

    private void startCommandReceived(long chatId, String firstName) {
        String answer = EmojiParser.parseToUnicode("Hi, " + firstName + "!\nNice to meet you! \uD83D\uDE0A");
        log.info("Replied to user: " + answer);
        sendMessage(chatId, answer);
    }

    private ReplyKeyboardMarkup generateKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        List<KeyboardRow> listRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("weather");
        row.add("get random joke");
        listRows.add(row);

        KeyboardRow row1 = new KeyboardRow();
        row1.add("register");
        row1.add("check my data");
        row1.add("delete my data");
        listRows.add(row1);

        markup.setKeyboard(listRows);

        return markup;
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage send = new SendMessage();
        send.setChatId(String.valueOf(chatId));
        send.setText(textToSend);

        send.setReplyMarkup(generateKeyboard());

        try {
            execute(send);
        } catch (TelegramApiException e) {
            log.error("Error occured on sending message: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }
}
