package cn.cerc.mis.excel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import cn.cerc.db.core.Datetime;
import cn.cerc.db.core.LanguageResource;

public class ExcelCellReader {

    public static Object getValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC || type == CellType.FORMULA) {
            if (DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return new Datetime(date).toString();
            } else {
                BigDecimal bigDecimal = new BigDecimal(cell.getNumericCellValue());
                if (LanguageResource.isLanguageTW())
                    bigDecimal = bigDecimal.setScale(4, RoundingMode.HALF_UP);
                else
                    bigDecimal = bigDecimal.setScale(4, RoundingMode.HALF_EVEN);
                return bigDecimal.stripTrailingZeros().toPlainString();
            }
        } else if (type == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        return null;
    }

    public static String getString(Cell cell) {
        Object value = ExcelCellReader.getValue(cell);
        if (value == null) {
            return "";
        } else if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Date) {
            Datetime tmp = new Datetime((Date) value);
            return tmp.toString();
        } else if (value instanceof Float || value instanceof Double) {
            String str = value.toString();
            if (str.endsWith(".0"))
                return str.substring(0, str.length() - 2);
            else
                return str;
        } else {
            return value.toString();
        }
    }

    public static double getDouble(Cell cell) {
        Object value = ExcelCellReader.getValue(cell);
        if (value == null) {
            return 0;
        } else if ((value instanceof Boolean)) {
            return (Boolean) value ? 1 : 0;
        } else if ((value instanceof Short)) {
            return ((Short) value);
        } else if (value instanceof Integer) {
            return ((Integer) value);
        } else if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Float) {
            return (Float) value;
        } else if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof String) {
            String str = (String) value;
            if ("".equals(str))
                return 0;
            try {
                return new BigDecimal(str).doubleValue();
            } catch (Exception e) {
                throw new RuntimeException(String.format("Error converting value %s to double", str));
            }
        } else {
            throw new ClassCastException(String.format("not support class: %s", value.getClass().getName()));
        }
    }

}
