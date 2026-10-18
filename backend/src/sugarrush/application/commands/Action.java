package sugarrush.application.commands;

import java.util.*;
import sugarrush.domain.user.Role;

public enum Action {
    SESSION_INFO, LOGOUT, ROLE_MENU, BROWSE_RESTAURANTS, LIST_ORDERS, PLACE_ORDER, CANCEL_ORDER, RESTAURANT_QUEUE,
    ACCEPT_ORDER, REJECT_ORDER, START_PREPARING, MARK_READY, SET_RESTAURANT_STATUS, PUT_MENU_ITEM, DASHBOARD, EVENT_LOG,
    COMMAND_LOG, CREATE_RESTAURANT, DELETE_MENU_ITEM;

    public boolean permits(Role role) {
        return switch (this) {
        case SESSION_INFO, LOGOUT, ROLE_MENU, BROWSE_RESTAURANTS -> true;
        case PLACE_ORDER -> role == Role.CUSTOMER;
        case CANCEL_ORDER -> role == Role.CUSTOMER || role == Role.OPERATOR;
        case LIST_ORDERS -> role != Role.COURIER;
        case RESTAURANT_QUEUE, ACCEPT_ORDER, REJECT_ORDER, START_PREPARING, MARK_READY, PUT_MENU_ITEM,
                CREATE_RESTAURANT, DELETE_MENU_ITEM ->
            role == Role.RESTAURANT;
        case SET_RESTAURANT_STATUS -> role == Role.RESTAURANT || role == Role.OPERATOR;
        case DASHBOARD, EVENT_LOG, COMMAND_LOG -> role == Role.OPERATOR;
        };
    }

    public static List<Action> menu(Role role) {
        return Arrays.stream(values()).filter(a -> a.permits(role)).toList();
    }
}
