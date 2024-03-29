package cn.cerc.mis.excel.output;

public class DateColumn extends Column {

    public DateColumn() {
        super();
    }

    public DateColumn(String code, String name, int width) {
        super(code, name, width);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Object getValue() {
        return getRecord().has(getCode()) ? getRecord().getDate(getCode()) : "";
    }
}
