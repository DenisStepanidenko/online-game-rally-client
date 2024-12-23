package org.example.validator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PasswordValidator implements Validator{
    @Override
    public boolean validate(String password) {
        // Регулярное выражение для валидации имени пользователя
        String regex = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?])[a-zA-Z\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{5,}$";

        // Компиляция регулярного выражения
        Pattern pattern = Pattern.compile(regex);

        // Проверка имени пользователя
        Matcher matcher = pattern.matcher(password);

        return matcher.matches();
    }
}
