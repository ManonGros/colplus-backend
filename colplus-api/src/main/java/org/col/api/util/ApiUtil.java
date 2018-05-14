package org.col.api.util;

import java.util.Objects;

import org.col.api.model.Dataset;
import org.col.api.model.Name;
import org.col.api.model.Reference;
import org.col.api.model.Taxon;

class ApiUtil {

	static boolean equalsShallow(Dataset obj1, Dataset obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	static boolean equalsShallow(Taxon obj1, Taxon obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	static boolean equalsShallow(Reference obj1, Reference obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	static boolean equalsShallow(Name obj1, Name obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	private ApiUtil() {
	}

}
