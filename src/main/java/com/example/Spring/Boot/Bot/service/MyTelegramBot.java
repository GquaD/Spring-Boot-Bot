package com.example.Spring.Boot.Bot.service;

import com.example.Spring.Boot.Bot.config.BotConfig;
import com.example.Spring.Boot.Bot.model.User;
import com.example.Spring.Boot.Bot.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MyTelegramBot extends TelegramLongPollingBot {

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
                default:
                    sendMessage(chatId, "Sorry, the command is not recognized.");
            }
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
        String answer = "Hi, " + firstName + "!\nNice to meet you!";
        log.info("Replied to user: " + answer);
        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage send = new SendMessage();
        send.setChatId(String.valueOf(chatId));
        send.setText(textToSend);
        ;

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
