package org.example.validator;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class UsernameValidator implements Validator {

    @Override
    public boolean validate(String username) {
        // Регулярное выражение для валидации имени пользователя
        String regex = "^(?=.*\\d)(?=.*[a-zA-Z])[a-zA-Z\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{5,20}$";

        // Компиляция регулярного выражения
        Pattern pattern = Pattern.compile(regex);

        // Проверка имени пользователя
        Matcher matcher = pattern.matcher(username);

        return matcher.matches();
    }
}
