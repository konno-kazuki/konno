package jp.co.fujielectric.fss.util;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

/**
 * Beanクラスのユーティリティクラス
 *
 */
public class BeanUtils {

	public static Object beanToBean(Object toDto, Object fromDto) {
			try {
				BeanInfo bi = Introspector.getBeanInfo(fromDto.getClass());

				PropertyDescriptor[] fromProperties = bi.getPropertyDescriptors();

				for (int i = 0; i < fromProperties.length; i++) {
					PropertyDescriptor fromDescriptor = fromProperties[i];
					String name = fromDescriptor.getName();
					Method getter = fromDescriptor.getReadMethod();
					if (getter != null) {
						PropertyDescriptor toDescriptor = getPropertyDescriptors(toDto, name);
						if (toDescriptor != null) {
							Method setter = toDescriptor.getWriteMethod();
							if (setter != null) {
								Object value = null;
								try {
									value = getter.invoke(fromDto, null);
									Object[] obj = new Object[1];
									obj[0] = value;
									setter.invoke(toDto, obj);
								} catch (Exception e1) {
									e1.printStackTrace();
								}
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		return toDto;
	}

	private static PropertyDescriptor getPropertyDescriptors(Object dto, String name) {
		PropertyDescriptor propertyDescriptor = null;

		try {
			BeanInfo bi = Introspector.getBeanInfo(dto.getClass());

			PropertyDescriptor[] properties = bi.getPropertyDescriptors();

			for (int i = 0; i < properties.length; i++) {
				if (name.equals(properties[i].getName())) {
					propertyDescriptor = properties[i];
					break;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return propertyDescriptor;
	}

	public static Object nullToEmptyString(Object dto) {
		try {
			BeanInfo bi = Introspector.getBeanInfo(dto.getClass());

			PropertyDescriptor[] properties = bi.getPropertyDescriptors();

			for (int i = 0; i < properties.length; i++) {

				Method setter = properties[i].getWriteMethod();
				Method getter = properties[i].getReadMethod();
				if (setter == null || getter == null) {
					continue;
				}

				Class returnType = getter.getReturnType();
				if (returnType != String.class) {
					continue;
				}

				Object returnObj = getter.invoke(dto, new Object[0]);
				if (returnObj == null) {
					String[] obj = new String[1];
					obj[0] = "";

					setter.invoke(dto, obj);
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return dto;
	}
}
