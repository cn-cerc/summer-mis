package cn.cerc.mis.core;

public interface IStatus {

    int getState();

    Object setState(int state);

    String getMessage();

    Object setMessage(String message);

    default boolean getResult() {
        return this.getState() > 0;
    }
}
