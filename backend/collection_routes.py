"""Capability-scoped deletion and administrator-only reads. Never return tokens."""
import secrets
from typing import Annotated
from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response
from config import Settings, get_settings
from dependencies import get_collection_service
from collection_service import CollectionService, valid_token

router = APIRouter(prefix='/collection', tags=['collection'])


def admin(settings: Annotated[Settings, Depends(get_settings)],
          authorization: Annotated[str | None, Header()] = None):
    expected = settings.collection_admin_token
    if len(expected) < 32 or not secrets.compare_digest((authorization or '').encode('utf-8'), ('Bearer ' + expected).encode('utf-8')):
        raise HTTPException(401, 'Доступ заборонено')


def repository(service: Annotated[CollectionService, Depends(get_collection_service)]):
    if service.repository is None:
        raise HTTPException(503, 'Серверне збереження не налаштоване')
    return service.repository


@router.delete('/mine')
def delete_mine(response: Response, repo=Depends(repository),
                x_collection_token: Annotated[str | None, Header()] = None):
    response.headers['Cache-Control'] = 'no-store'
    if not valid_token(x_collection_token):
        raise HTTPException(401, 'Недійсний ключ встановлення')
    try:
        return {'deleted': repo.delete(x_collection_token)}
    except Exception:
        raise HTTPException(503, 'Не вдалося видалити записи. Спробуйте пізніше.') from None


@router.get('/admin/records', dependencies=[Depends(admin)])
def records(response: Response, repo=Depends(repository), limit: int = Query(50, ge=1, le=100),
            offset: int = Query(0, ge=0)):
    response.headers['Cache-Control'] = 'no-store'
    return {'items': repo.records(limit, offset), 'limit': limit, 'offset': offset}


@router.get('/admin/stats', dependencies=[Depends(admin)])
def stats(response: Response, repo=Depends(repository)):
    response.headers['Cache-Control'] = 'no-store'
    return repo.stats()
