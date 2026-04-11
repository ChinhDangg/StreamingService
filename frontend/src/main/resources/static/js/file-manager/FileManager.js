export class FileManager {

    constructor() {
        this.currentFileItemMap = new Map();
        this.currentFileItemIds = [];
        this.currentFilePage = -1;
    }

    setCurrentFilePage(page) {
        this.currentFilePage = page;
    }

    getCurrentFilePage() {
        return this.currentFilePage;
    }

    addFileItem(fileItem) {
        this.currentFileItemMap.set(fileItem.id, fileItem);
        this.currentFileItemIds.push(fileItem.id);
    }

    removeFileItem(fileItemId) {
        this.currentFileItemMap.delete(fileItemId);
        this.currentFileItemIds.splice(this.currentFileItemIds.indexOf(fileItemId), 1);
    }

    removeFileItemInMapOnly(fileItemId) {
        this.currentFileItemMap.delete(fileItemId);
    }

    removeFileItemsInIdListOnly(fileItemIds) {
        this.currentFileItemIds = this.currentFileItemIds.filter(id => !fileItemIds.has(id));
    }

    removeAll() {
        this.currentFileItemMap.clear();
        this.currentFileItemIds = [];
    }

    getFileItemById(fileItemId) {
        return this.currentFileItemMap.get(fileItemId);
    }

    findOneFileItemWithExactName(name) {
        for (const fileItem of this.currentFileItemMap.values())
            if (fileItem.name === name)
                return fileItem;
        return null;
    }

    findFileItemsWithNameAndReturnTheirIds(name) {
        const fileItemIds = [];
        for (const [key, fileItem] of this.currentFileItemMap)
            if (fileItem.name.toLowerCase().includes(name.toLowerCase()))
                fileItemIds.push(key);
        return fileItemIds;
    }

    loopAllAndDo(callback) {
        this.currentFileItemIds.forEach(id => {
            callback(this.currentFileItemMap.get(id));
        });
    }

    loopAndDoWithGivenFileItemIds(fileItemIds, callback) {
        fileItemIds.forEach(id => {
            callback(this.currentFileItemMap.get(id));
        });
    }

    sortFileItems(key, order = 'ASC') {
        this.currentFileItemIds.sort(this.sortIdsByMap(key, order));
    }

    sortIdsByMap(key, order = 'ASC', fileItemMap = this.currentFileItemMap) {
        // We get the original comparison logic
        const internalSort = this.dynamicSortByField(key, order);

        // We return a new function that expects IDs instead of Objects
        return function(idA, idB) {
            const objA = fileItemMap.get(idA);
            const objB = fileItemMap.get(idB);

            // Pass the actual objects into your existing logic
            return internalSort(objA, objB);
        };
    }

    dynamicSortByField(key, order = 'ASC') {
        const getValue = (obj, path) => {
            // Splitting by dot and reducing through the object
            return path.split('.').reduce((acc, part) => acc?.[part], obj);
        };

        return function innerSort(a, b) {
            let varA = getValue(a, key);
            let varB = getValue(b, key);
            if (!varA || !varB) {
                return 0;
            }
            const valA = (typeof varA === 'string') ? varA.toUpperCase() : varA;
            const valB = (typeof varB === 'string') ? varB.toUpperCase() : varB;
            let comparison = 0;
            if (valA > valB) {
                comparison = 1;
            } else if (valA < valB) {
                comparison = -1;
            }
            return (order === 'DESC') ? (comparison * -1) : comparison;
        }
    }
}