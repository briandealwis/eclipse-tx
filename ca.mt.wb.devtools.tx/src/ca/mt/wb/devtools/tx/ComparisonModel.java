/*
 * Written by Brian de Alwis.
 * Released under the <a href="http://unlicense.org">UnLicense</a>
 */
package ca.mt.wb.devtools.tx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

public class ComparisonModel {
	protected Map<IType, ITypeHierarchy> added = new HashMap<IType, ITypeHierarchy>();
	protected Set<IType> removed = new HashSet<IType>();
    
    public ComparisonModel() {}
    
    public void add(IType t) {
        if(contains(t)) { return; } // skip it
		removed.remove(t); // in case it was previously removed
        try {
			added.put(t, t.newSupertypeHierarchy(new NullProgressMonitor()));
        } catch (JavaModelException e) {
             /* do nothing */
        }
    }
  
    public boolean remove(IType t) {
		boolean result = isShowing(t);
		removed.add(t);
		added.remove(t);
		return result;
    }
    
    public boolean contains(IType t) {
		return added.containsKey(t);
    }

    public IType getSuperclassFor(IType t) {
		for (ITypeHierarchy h : added.values()) {
			IType sup = h.getSuperclass(t);
			if (sup != null) {
				// don't include if explicitly deleted
				return isHidden(sup) ? null : sup;
			}
        }
        return null;
    }

	private boolean isShowing(IType t) {
		if (added.containsKey(t)) {
			return true;
		}
		for (ITypeHierarchy h : added.values()) {
			if (h.getSupertypes(t) != null) {
				return true;
			}
		}
		return false;
	}

	private boolean isHidden(IType sup) {
		return "java.lang.Object".equals(sup.getFullyQualifiedName())
				|| removed.contains(sup);
	}
    
    public IType[] getSuperinterfacesFor(IType t) {
        Set<IType> supers = new HashSet<IType>();
		for (ITypeHierarchy h : added.values()) {
			for (IType superinterface : h.getSuperInterfaces(t)) {
				if (!isHidden(superinterface)) {
					supers.add(superinterface);
				}
            }
        }
        return (IType[])supers.toArray(new IType[supers.size()]);
    }

    public ComparisonModel copy() {
        ComparisonModel newInstance = new ComparisonModel();
		newInstance.added = new HashMap<IType, ITypeHierarchy>(added);
		newInstance.removed = new HashSet<IType>(removed);
        return newInstance;
    }
    
    public Set<IType> getTypes() {
		return added.keySet();
    }

    public Set<IType> getAllTypes() {
		Set<IType> results = new HashSet<IType>();
		for (ITypeHierarchy h : added.values()) {
			getAllTypes(results, h, h.getType());
        }
		return results;
    }

	private void getAllTypes(Set<IType> results, ITypeHierarchy h, IType type) {
		if (isHidden(type) || results.contains(type)) {
			return;
		}
		results.add(type);
		for (IType sup : h.getSupertypes(type)) {
			getAllTypes(results, h, sup);
		}
	}
}
